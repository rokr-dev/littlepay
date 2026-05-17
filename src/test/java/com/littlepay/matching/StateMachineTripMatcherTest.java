package com.littlepay.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.littlepay.domain.Money;
import com.littlepay.domain.Pan;
import com.littlepay.domain.StopId;
import com.littlepay.domain.StopPair;
import com.littlepay.domain.Tap;
import com.littlepay.domain.TapType;
import com.littlepay.domain.Trip;
import com.littlepay.domain.TripStatus;
import com.littlepay.pricing.FareTable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 20 matcher scenarios from PRD §Testing Decisions > Matcher Scenarios (1-20).
 * Scenarios 21-23 cover CSV/IO parsing — not in scope here.
 *
 * Fake FareTable uses a simple two-stop matrix:
 *   Stop1 <-> Stop2 = $3.25
 *   Stop1 <-> Stop3 = $7.30
 *   Stop2 <-> Stop3 = $5.50
 */
class StateMachineTripMatcherTest {

    private static final Currency AUD = Currency.getInstance("AUD");
    private static final StopId STOP1 = new StopId("Stop1");
    private static final StopId STOP2 = new StopId("Stop2");
    private static final StopId STOP3 = new StopId("Stop3");
    private static final Pan PAN_A = new Pan("1234567890001234");
    private static final Pan PAN_B = new Pan("9999888877776666");
    private static final String COMPANY = "Company1";
    private static final String BUS_A = "Bus37";
    private static final String BUS_B = "Bus99";

    /** $3.25 AUD */
    private static final Money FARE_1_2 = Money.of(new BigDecimal("3.25"), AUD);
    /** $7.30 AUD — max from Stop1 */
    private static final Money FARE_1_3 = Money.of(new BigDecimal("7.30"), AUD);
    /** $5.50 AUD — max from Stop2 */
    private static final Money FARE_2_3 = Money.of(new BigDecimal("5.50"), AUD);
    private static final Money ZERO = Money.of(BigDecimal.ZERO, AUD);

    private FareTable fareTable;
    private StateMachineTripMatcher matcher;

    @BeforeEach
    void setUp() {
        fareTable = new FakeFareTable();
        matcher = new StateMachineTripMatcher(fareTable);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Tap on(long id, LocalDateTime dt, StopId stop, Pan pan) {
        return new Tap(id, dt, TapType.ON, stop, COMPANY, BUS_A, pan);
    }

    private static Tap on(long id, LocalDateTime dt, StopId stop, Pan pan, String bus) {
        return new Tap(id, dt, TapType.ON, stop, COMPANY, bus, pan);
    }

    private static Tap off(long id, LocalDateTime dt, StopId stop, Pan pan) {
        return new Tap(id, dt, TapType.OFF, stop, COMPANY, BUS_A, pan);
    }

    private static Tap off(long id, LocalDateTime dt, StopId stop, Pan pan, String bus) {
        return new Tap(id, dt, TapType.OFF, stop, COMPANY, bus, pan);
    }

    private static LocalDateTime timeAt(int hour, int minute, int second) {
        return LocalDateTime.of(2023, 1, 15, hour, minute, second);
    }

    private static LocalDateTime timeAtDay2(int hour, int minute, int second) {
        // second day
        return LocalDateTime.of(2023, 1, 16, hour, minute, second);
    }

    // ── 1. completesTripWhenOnAndOffAtDifferentStops ─────────────────────────

    @Test
    void completesTripWhenOnAndOffAtDifferentStops() {
        // arrange
        Tap tapOn  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap tapOff = off(2, timeAt(9, 5, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn, tapOff));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.getFirst();
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.fromStop()).isEqualTo(STOP1);
        assertThat(trip.toStop()).isEqualTo(STOP2);
        assertThat(trip.chargeAmount()).isEqualTo(FARE_1_2);
        assertThat(trip.started()).isEqualTo(timeAt(9, 0, 0));
        assertThat(trip.finished()).isEqualTo(timeAt(9, 5, 0));
    }

    // ── 2. cancelsTripWhenOnAndOffAtSameStop ─────────────────────────────────

    @Test
    void cancelsTripWhenOnAndOffAtSameStop() {
        // arrange
        Tap tapOn  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap tapOff = off(2, timeAt(9, 2, 0), STOP1, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn, tapOff));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.getFirst();
        assertThat(trip.status()).isEqualTo(TripStatus.CANCELLED);
        assertThat(trip.chargeAmount()).isEqualTo(ZERO);
    }

    // ── 3. marksTripIncompleteWhenOnHasNoOffBeforeEod ────────────────────────

    @Test
    void marksTripIncompleteWhenOnHasNoOffBeforeEod() {
        // arrange
        Tap tapOn = on(1, timeAt(9, 0, 0), STOP1, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.getFirst();
        assertThat(trip.status()).isEqualTo(TripStatus.INCOMPLETE);
        // max fare from Stop1 is Stop1<->Stop3 = $7.30
        assertThat(trip.chargeAmount()).isEqualTo(FARE_1_3);
        assertThat(trip.fromStop()).isEqualTo(STOP1);
        // passenger never tapped off — finished and toStop are null
        assertThat(trip.finished()).isNull();
        assertThat(trip.toStop()).isNull();
    }

    // ── 4. emitsUnmatchedOffWhenOffArrivesWithNoPriorOn ──────────────────────

    @Test
    void emitsUnmatchedOffWhenOffArrivesWithNoPriorOn() {
        // arrange
        Tap tapOff = off(1, timeAt(9, 0, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOff));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.getFirst();
        assertThat(trip.status()).isEqualTo(TripStatus.UNMATCHED_OFF);
        assertThat(trip.chargeAmount()).isEqualTo(ZERO);
        assertThat(trip.toStop()).isEqualTo(STOP2);
        assertThat(trip.fromStop()).isNull();
        assertThat(trip.started()).isNull();
    }

    // ── 5. dedupsSameStopConsecutiveOnsWithinDuplicateWindow ─────────────────

    @Test
    void dedupsSameStopConsecutiveOnsWithinDuplicateWindow() {
        // arrange: second ON at same stop, 10s later (within 30s window)
        Tap on1  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, timeAt(9, 0, 10), STOP1, PAN_A);
        Tap off1 = off(3, timeAt(9, 5, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, on2, off1));

        // assert: only one trip — second ON was dropped; first ON remains active
        assertThat(trips).hasSize(1);
        Trip trip = trips.getFirst();
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.started()).isEqualTo(timeAt(9, 0, 0)); // first ON
    }

    // ── 6. splitsSameStopConsecutiveOnsOutsideDuplicateWindow ────────────────

    @Test
    void splitsSameStopConsecutiveOnsOutsideDuplicateWindow() {
        // arrange: second ON at same stop, 60s later (outside 30s window)
        Tap on1  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, timeAt(9, 1, 0), STOP1, PAN_A);
        Tap off1 = off(3, timeAt(9, 5, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, on2, off1));

        // assert: first ON closed as INCOMPLETE, second ON becomes COMPLETED
        assertThat(trips).hasSize(2);
        Trip incomplete = trips.stream()
                .filter(t -> t.status() == TripStatus.INCOMPLETE)
                .findFirst().orElseThrow();
        Trip completed = trips.stream()
                .filter(t -> t.status() == TripStatus.COMPLETED)
                .findFirst().orElseThrow();

        assertThat(incomplete.fromStop()).isEqualTo(STOP1);
        assertThat(completed.started()).isEqualTo(timeAt(9, 1, 0));
    }

    // ── 7. collapsesCrossStopConsecutiveOnsWithinDuplicateWindow ─────────────

    @Test
    void collapsesCrossStopConsecutiveOnsWithinDuplicateWindow() {
        // arrange: second ON at different stop, 5s later (within window)
        Tap on1  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, timeAt(9, 0, 5), STOP2, PAN_A);
        Tap off1 = off(3, timeAt(9, 5, 0), STOP3, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, on2, off1));

        // assert: second ON dropped; first ON active → COMPLETED from Stop1 to Stop3
        assertThat(trips).hasSize(1);
        Trip trip = trips.getFirst();
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.started()).isEqualTo(timeAt(9, 0, 0));
        assertThat(trip.fromStop()).isEqualTo(STOP1);
        assertThat(trip.toStop()).isEqualTo(STOP3);
    }

    // ── 8. splitsCrossStopConsecutiveOnsOutsideDuplicateWindow ───────────────

    @Test
    void splitsCrossStopConsecutiveOnsOutsideDuplicateWindow() {
        // arrange: second ON at different stop, 60s later (outside window)
        Tap on1  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, timeAt(9, 1, 0), STOP2, PAN_A);
        Tap off1 = off(3, timeAt(9, 5, 0), STOP3, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, on2, off1));

        // assert: first ON → INCOMPLETE; second ON → COMPLETED (Stop2→Stop3)
        assertThat(trips).hasSize(2);
        Trip incomplete = trips.stream()
                .filter(t -> t.status() == TripStatus.INCOMPLETE)
                .findFirst().orElseThrow();
        Trip completed = trips.stream()
                .filter(t -> t.status() == TripStatus.COMPLETED)
                .findFirst().orElseThrow();

        assertThat(incomplete.fromStop()).isEqualTo(STOP1);
        assertThat(completed.fromStop()).isEqualTo(STOP2);
        assertThat(completed.toStop()).isEqualTo(STOP3);
    }

    // ── 9. handlesDoubleOffByTreatingSecondAsUnmatched ────────────────────────

    @Test
    void handlesDoubleOffByTreatingSecondAsUnmatched() {
        // arrange
        Tap on1  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap off1 = off(2, timeAt(9, 5, 0), STOP2, PAN_A);
        Tap off2 = off(3, timeAt(9, 6, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1, off2));

        // assert: first pair = COMPLETED, second OFF = UNMATCHED_OFF
        assertThat(trips).hasSize(2);
        long completed = trips.stream().filter(t -> t.status() == TripStatus.COMPLETED).count();
        long unmatched = trips.stream().filter(t -> t.status() == TripStatus.UNMATCHED_OFF).count();
        assertThat(completed).isEqualTo(1);
        assertThat(unmatched).isEqualTo(1);
    }

    // ── 10. pairsTapsWithinSamePanAndBusOnly ─────────────────────────────────

    @Test
    void pairsTapsWithinSamePanAndBusOnly() {
        // arrange: PAN_A on BUS_A taps ON; PAN_A on BUS_B taps OFF — should NOT pair
        Tap on1  = on(1, timeAt(9, 0, 0), STOP1, PAN_A, BUS_A);
        Tap off1 = off(2, timeAt(9, 5, 0), STOP2, PAN_A, BUS_B);

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1));

        // assert: ON → INCOMPLETE, OFF → UNMATCHED_OFF
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.INCOMPLETE).count()).isEqualTo(1);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.UNMATCHED_OFF).count()).isEqualTo(1);
    }

    // ── 11. doesNotPairAcrossUtcDayBoundary ──────────────────────────────────

    @Test
    void doesNotPairAcrossUtcDayBoundary() {
        // arrange: ON day 1, OFF day 2 — different buckets
        Tap on1  = new Tap(1, LocalDateTime.of(2023, 1, 15, 23, 55, 0), TapType.ON, STOP1, COMPANY, BUS_A, PAN_A);
        Tap off1 = new Tap(2, LocalDateTime.of(2023, 1, 16, 0, 5, 0), TapType.OFF, STOP2, COMPANY, BUS_A, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1));

        // assert: ON → INCOMPLETE (day 1 bucket), OFF → UNMATCHED_OFF (day 2 bucket)
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.INCOMPLETE).count()).isEqualTo(1);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.UNMATCHED_OFF).count()).isEqualTo(1);
    }

    // ── 12. sortsUnorderedInputTapsWithinBucket ───────────────────────────────

    @Test
    void sortsUnorderedInputTapsWithinBucket() {
        // arrange: OFF provided before ON in input list
        Tap tapOff = off(2, timeAt(9, 5, 0), STOP2, PAN_A);
        Tap tapOn  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOff, tapOn));

        // assert: still produces a COMPLETED trip
        assertThat(trips).hasSize(1);
        assertThat(trips.getFirst().status()).isEqualTo(TripStatus.COMPLETED);
    }

    // ── 13. handlesMultipleConcurrentPansIndependently ───────────────────────

    @Test
    void handlesMultipleConcurrentPansIndependently() {
        // arrange: two PANs on same bus, interleaved taps
        Tap onA  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap onB  = on(2, timeAt(9, 0, 30), STOP1, PAN_B);
        Tap offA = off(3, timeAt(9, 5, 0), STOP2, PAN_A);
        Tap offB = off(4, timeAt(9, 5, 30), STOP3, PAN_B);

        // act
        List<Trip> trips = matcher.match(List.of(onA, onB, offA, offB));

        // assert: two COMPLETED trips, one per PAN
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().allMatch(t -> t.status() == TripStatus.COMPLETED)).isTrue();

        Trip tripA = trips.stream().filter(t -> t.pan().equals(PAN_A)).findFirst().orElseThrow();
        Trip tripB = trips.stream().filter(t -> t.pan().equals(PAN_B)).findFirst().orElseThrow();
        assertThat(tripA.toStop()).isEqualTo(STOP2);
        assertThat(tripB.toStop()).isEqualTo(STOP3);
    }

    // ── 14. calculatesDurationInSecondsCorrectly ─────────────────────────────

    @Test
    void calculatesDurationInSecondsCorrectly() {
        // arrange: 5 minutes 30 seconds = 330 seconds
        Tap tapOn  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap tapOff = off(2, timeAt(9, 5, 30), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn, tapOff));

        // assert
        assertThat(trips).hasSize(1);
        assertThat(trips.getFirst().durationSecs()).isEqualTo(330L);
    }

    // ── 15. preservesCompanyAndBusFromOnTapOnEmittedTrip ─────────────────────

    @Test
    void preservesCompanyAndBusFromOnTapOnEmittedTrip() {
        // arrange: ON on BUS_A, OFF also on BUS_A (matched by bucket)
        Tap tapOn  = on(1, timeAt(9, 0, 0), STOP1, PAN_A, BUS_A);
        Tap tapOff = off(2, timeAt(9, 5, 0), STOP2, PAN_A, BUS_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn, tapOff));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.getFirst();
        assertThat(trip.companyId()).isEqualTo(COMPANY);
        assertThat(trip.busId()).isEqualTo(BUS_A);
    }

    // ── 16. multipleTripsPerPassengerPerDay ──────────────────────────────────

    @Test
    void multipleTripsPerPassengerPerDay() {
        // arrange: two complete trips for PAN_A on same day and bus
        Tap on1  = on(1, timeAt(8, 0, 0), STOP1, PAN_A);
        Tap off1 = off(2, timeAt(8, 5, 0), STOP2, PAN_A);
        Tap on2  = on(3, timeAt(9, 0, 0), STOP2, PAN_A);
        Tap off2 = off(4, timeAt(9, 10, 0), STOP3, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1, on2, off2));

        // assert
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().allMatch(t -> t.status() == TripStatus.COMPLETED)).isTrue();
    }

    // ── 17. roundTripChargesBothLegs ─────────────────────────────────────────

    @Test
    void roundTripChargesBothLegs() {
        // arrange: Stop1→Stop2, then Stop2→Stop1
        Tap on1  = on(1, timeAt(8, 0, 0), STOP1, PAN_A);
        Tap off1 = off(2, timeAt(8, 5, 0), STOP2, PAN_A);
        Tap on2  = on(3, timeAt(9, 0, 0), STOP2, PAN_A);
        Tap off2 = off(4, timeAt(9, 10, 0), STOP1, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1, on2, off2));

        // assert: both legs charged $3.25 (Stop1<->Stop2 pair)
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().allMatch(t -> t.chargeAmount().equals(FARE_1_2))).isTrue();
    }

    // ── 18. breaksTiesOnIdenticalTimestampsUsingInputIdAscending ─────────────

    @Test
    void breaksTiesOnIdenticalTimestampsUsingInputIdAscending() {
        // arrange: two ONs at identical timestamp; id=1 should be kept, id=2 dropped (within window)
        Tap on1  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, timeAt(9, 0, 0), STOP2, PAN_A); // same timestamp, id=2
        Tap off1 = off(3, timeAt(9, 5, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on2, on1, off1)); // deliberately unsorted

        // assert: only one trip, started from the ON with id=1 (STOP1)
        assertThat(trips).hasSize(1);
        Trip trip = trips.getFirst();
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.fromStop()).isEqualTo(STOP1); // id=1 wins the tiebreak
    }

    // ── 19. newOnAfterCancelledWithinWindowStartsNewTrip ─────────────────────

    @Test
    void newOnAfterCancelledWithinWindowStartsNewTrip() {
        // arrange: ON+OFF same stop (CANCELLED), then new ON within 30s
        Tap on1  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap off1 = off(2, timeAt(9, 0, 10), STOP1, PAN_A); // CANCELLED
        Tap on2  = on(3, timeAt(9, 0, 15), STOP2, PAN_A);  // new ON within window of off1
        Tap off2 = off(4, timeAt(9, 5, 0), STOP3, PAN_A);  // completes trip from STOP2

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1, on2, off2));

        // assert: CANCELLED + COMPLETED
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.CANCELLED).count()).isEqualTo(1);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.COMPLETED).count()).isEqualTo(1);

        Trip completed = trips.stream().filter(t -> t.status() == TripStatus.COMPLETED).findFirst().orElseThrow();
        assertThat(completed.fromStop()).isEqualTo(STOP2);
    }

    // ── 20. tripleOnWithinWindowDropsAllButFirstOn ────────────────────────────

    @Test
    void tripleOnWithinWindowDropsAllButFirstOn() {
        // arrange: three ONs within 30s window; first remains active
        Tap on1  = on(1, timeAt(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, timeAt(9, 0, 5), STOP2, PAN_A);
        Tap on3  = on(3, timeAt(9, 0, 10), STOP3, PAN_A);
        Tap off1 = off(4, timeAt(9, 5, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, on2, on3, off1));

        // assert: only one trip — first ON (STOP1) remains active throughout
        assertThat(trips).hasSize(1);
        Trip trip = trips.getFirst();
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.fromStop()).isEqualTo(STOP1);
        assertThat(trip.toStop()).isEqualTo(STOP2);
    }

    // ── 21. doesNotPairTapsAcrossDifferentCompaniesOnSameBus ─────────────────

    @Test
    void doesNotPairTapsAcrossDifferentCompaniesOnSameBus() {
        // arrange: same PAN, same busId, same day — but different companies
        // Without companyId in BucketKey these land in the same bucket and pair incorrectly
        String bus = "Bus1";
        Tap tapOn  = new Tap(1, timeAt(9, 0, 0),  TapType.ON,  STOP1, "Company1", bus, PAN_A);
        Tap tapOff = new Tap(2, timeAt(9, 5, 0),  TapType.OFF, STOP2, "Company2", bus, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn, tapOff));

        // assert: ON → INCOMPLETE (Company1), OFF → UNMATCHED_OFF (Company2)
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.INCOMPLETE).count()).isEqualTo(1);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.UNMATCHED_OFF).count()).isEqualTo(1);
    }

    // ── Fake FareTable ────────────────────────────────────────────────────────

    /**
     * Fake in-memory fare table:
     *   Stop1 <-> Stop2 = $3.25
     *   Stop1 <-> Stop3 = $7.30  (max from Stop1)
     *   Stop2 <-> Stop3 = $5.50  (max from Stop2)
     */
    private static final class FakeFareTable implements FareTable {

        @Override
        public Money fareFor(StopPair pair) {
            String a = pair.first().value();
            String b = pair.second().value();
            // StopPair normalises lexicographically: Stop1 < Stop2 < Stop3
            if (a.equals("Stop1") && b.equals("Stop2")) {
                return FARE_1_2;
            }
            if (a.equals("Stop1") && b.equals("Stop3")) {
                return FARE_1_3;
            }
            if (a.equals("Stop2") && b.equals("Stop3")) {
                return FARE_2_3;
            }
            throw new IllegalArgumentException("No fare for " + pair);
        }

        @Override
        public Money maxFareFrom(StopId stop) {
            return switch (stop.value()) {
                case "Stop1" -> FARE_1_3; // $7.30
                case "Stop2" -> FARE_2_3; // $5.50
                case "Stop3" -> FARE_1_3; // $7.30
                default -> throw new IllegalArgumentException("Unknown stop: " + stop.value());
            };
        }
    }
}
