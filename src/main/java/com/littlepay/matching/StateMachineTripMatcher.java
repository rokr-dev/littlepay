package com.littlepay.matching;

import com.littlepay.cli.CliArgs;
import com.littlepay.domain.Money;
import com.littlepay.domain.Pan;
import com.littlepay.domain.StopPair;
import com.littlepay.domain.Tap;
import com.littlepay.domain.TapType;
import com.littlepay.domain.Trip;
import com.littlepay.domain.TripStatus;
import com.littlepay.pricing.FareTable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State-machine trip matcher.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Bucket taps by (PAN, CompanyID, BusID, UTC calendar-day).</li>
 *   <li>Sort each bucket: DateTimeUTC asc, tap ID asc (deterministic tiebreak).</li>
 *   <li>Run a two-state machine per bucket:
 *     <ul>
 *       <li><b>Waiting</b> — no active ON held.</li>
 *       <li><b>OnHeld(tap)</b> — an ON tap is held, waiting for its OFF.</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public final class StateMachineTripMatcher implements TripMatcher {

    private sealed interface TripState permits TripState.Waiting, TripState.OnHeld {
        record Waiting() implements TripState {}

        record OnHeld(Tap tap) implements TripState {}
    }

    private static final Logger log = LoggerFactory.getLogger(StateMachineTripMatcher.class);
    private static final Money ZERO_AUD = Money.of(BigDecimal.ZERO, Currency.getInstance("AUD"));

    private final FareTable fareTable;
    private final int duplicateWindowSecs;

    public StateMachineTripMatcher(FareTable fareTable) {
        this(fareTable, CliArgs.DEFAULT_DUPLICATE_WINDOW_SECONDS);
    }

    public StateMachineTripMatcher(FareTable fareTable, int duplicateWindowSecs) {
        if (fareTable == null) {
            throw new IllegalArgumentException("fareTable must not be null");
        }
        this.fareTable = fareTable;
        this.duplicateWindowSecs = duplicateWindowSecs;
    }

    @Override
    public List<Trip> match(List<Tap> taps) {
        // Group into (PAN, BusID, UTC-day) buckets
        Map<BucketKey, List<Tap>> buckets = taps.stream()
                .collect(Collectors.groupingBy(BucketKey::of));

        List<Trip> results = new ArrayList<>();
        for (Map.Entry<BucketKey, List<Tap>> entry : buckets.entrySet()) {
            List<Tap> bucket = entry.getValue();
            // Sort: timestamp asc, then tap-id asc for identical timestamps
            bucket.sort(Comparator.comparing(Tap::dateTime).thenComparingLong(Tap::id));
            results.addAll(processBucket(bucket));
        }
        return results;
    }

    private List<Trip> processBucket(List<Tap> sortedTaps) {
        List<Trip> trips = new ArrayList<>();
        TripState state = new TripState.Waiting();

        for (Tap tap : sortedTaps) {
            if (tap.tapType() == TapType.ON) {
                if (state instanceof TripState.Waiting()) {
                    // waiting → onHeld
                    state = new TripState.OnHeld(tap);
                } else {
                    TripState.OnHeld onHeld = (TripState.OnHeld) state;
                    Tap held = onHeld.tap(); // onHeld + new ON
                    long gapSecs = ChronoUnit.SECONDS.between(held.dateTime(), tap.dateTime());
                    if (gapSecs <= duplicateWindowSecs) {
                        // within window → drop new ON (hardware misfire)
                        log.warn("Duplicate ON dropped within window: tap id={} pan={} bus={}",
                                tap.id(), tap.pan().masked(), tap.busId());
                        // remain in OnHeld(held) — do NOT update state
                    } else {
                        // outside window → emit prev as INCOMPLETE, hold new
                        trips.add(makeIncomplete(held));
                        state = new TripState.OnHeld(tap);
                    }
                }
            } else { // TapType.OFF
                if (state instanceof TripState.Waiting()) {
                    // waiting + OFF → UNMATCHED_OFF
                    trips.add(makeUnmatchedOff(tap));
                } else {
                    TripState.OnHeld onHeld = (TripState.OnHeld) state;
                    Tap held = onHeld.tap(); // onHeld + OFF
                    if (held.stopId().equals(tap.stopId())) {
                        trips.add(makeCancelled(held, tap));
                    } else {
                        trips.add(makeCompleted(held, tap));
                    }
                    state = new TripState.Waiting(); // back to waiting
                }
            }
        }

        // End of bucket — flush any remaining held ON as INCOMPLETE
        if (state instanceof TripState.OnHeld(Tap held)) {
            trips.add(makeIncomplete(held));
        }

        return trips;
    }

    // ── Trip factories ───────────────────────────────────────────────────────

    private Trip makeCompleted(Tap on, Tap off) {
        long durationSecs = ChronoUnit.SECONDS.between(on.dateTime(), off.dateTime());
        Money charge = fareTable.fareFor(new StopPair(on.stopId(), off.stopId()));
        return new Trip(
                on.dateTime(),
                off.dateTime(),
                durationSecs,
                on.stopId(),
                off.stopId(),
                charge,
                on.companyId(),
                on.busId(),
                on.pan(),
                TripStatus.COMPLETED
        );
    }

    private Trip makeCancelled(Tap on, Tap off) {
        long durationSecs = ChronoUnit.SECONDS.between(on.dateTime(), off.dateTime());
        return new Trip(
                on.dateTime(),
                off.dateTime(),
                durationSecs,
                on.stopId(),
                off.stopId(),
                ZERO_AUD,
                on.companyId(),
                on.busId(),
                on.pan(),
                TripStatus.CANCELLED
        );
    }

    private Trip makeIncomplete(Tap on) {
        Money charge = fareTable.maxFareFrom(on.stopId());
        return new Trip(
                on.dateTime(),
                null,           // finished is null — passenger never tapped off
                0L,
                on.stopId(),
                null,           // toStop is null — no destination recorded
                charge,
                on.companyId(),
                on.busId(),
                on.pan(),
                TripStatus.INCOMPLETE
        );
    }

    private Trip makeUnmatchedOff(Tap off) {
        return new Trip(
                null,           // started is null
                off.dateTime(),
                0L,
                null,           // fromStop is null
                off.stopId(),
                ZERO_AUD,
                off.companyId(),
                off.busId(),
                off.pan(),
                TripStatus.UNMATCHED_OFF
        );
    }

    // ── Bucket key ───────────────────────────────────────────────────────────

    private record BucketKey(Pan pan, String companyId, String busId, LocalDate utcDay) {
        static BucketKey of(Tap tap) {
            return new BucketKey(tap.pan(), tap.companyId(), tap.busId(), tap.dateTime().toLocalDate());
        }
    }
}
