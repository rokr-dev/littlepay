package com.littlepay.matching;

import com.littlepay.domain.*;
import com.littlepay.domain.pan.Pan;
import com.littlepay.pricing.FareTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static LocalDateTime t(int hour, int minute, int second) {
        return LocalDateTime.of(2023, 1, 15, hour, minute, second);
    }

    private static LocalDateTime t2(int hour, int minute, int second) {
        // second day
        return LocalDateTime.of(2023, 1, 16, hour, minute, second);
    }

    // ── 1. completes_trip_when_on_and_off_at_different_stops ─────────────────

    @Test
    void completes_trip_when_on_and_off_at_different_stops() {
        // arrange
        Tap tapOn  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap tapOff = off(2, t(9, 5, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn, tapOff));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.get(0);
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.fromStop()).isEqualTo(STOP1);
        assertThat(trip.toStop()).isEqualTo(STOP2);
        assertThat(trip.chargeAmount()).isEqualTo(FARE_1_2);
        assertThat(trip.started()).isEqualTo(t(9, 0, 0));
        assertThat(trip.finished()).isEqualTo(t(9, 5, 0));
    }

    // ── 2. cancels_trip_when_on_and_off_at_same_stop ─────────────────────────

    @Test
    void cancels_trip_when_on_and_off_at_same_stop() {
        // arrange
        Tap tapOn  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap tapOff = off(2, t(9, 2, 0), STOP1, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn, tapOff));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.get(0);
        assertThat(trip.status()).isEqualTo(TripStatus.CANCELLED);
        assertThat(trip.chargeAmount()).isEqualTo(ZERO);
    }

    // ── 3. marks_trip_incomplete_when_on_has_no_off_before_eod ───────────────

    @Test
    void marks_trip_incomplete_when_on_has_no_off_before_eod() {
        // arrange
        Tap tapOn = on(1, t(9, 0, 0), STOP1, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.get(0);
        assertThat(trip.status()).isEqualTo(TripStatus.INCOMPLETE);
        // max fare from Stop1 is Stop1<->Stop3 = $7.30
        assertThat(trip.chargeAmount()).isEqualTo(FARE_1_3);
        assertThat(trip.fromStop()).isEqualTo(STOP1);
    }

    // ── 4. emits_unmatched_off_when_off_arrives_with_no_prior_on ─────────────

    @Test
    void emits_unmatched_off_when_off_arrives_with_no_prior_on() {
        // arrange
        Tap tapOff = off(1, t(9, 0, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOff));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.get(0);
        assertThat(trip.status()).isEqualTo(TripStatus.UNMATCHED_OFF);
        assertThat(trip.chargeAmount()).isEqualTo(ZERO);
        assertThat(trip.toStop()).isEqualTo(STOP2);
        assertThat(trip.fromStop()).isNull();
        assertThat(trip.started()).isNull();
    }

    // ── 5. dedups_same_stop_consecutive_ons_within_duplicate_window ──────────

    @Test
    void dedups_same_stop_consecutive_ons_within_duplicate_window() {
        // arrange: second ON at same stop, 10s later (within 30s window)
        Tap on1  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, t(9, 0, 10), STOP1, PAN_A);
        Tap off1 = off(3, t(9, 5, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, on2, off1));

        // assert: only one trip — second ON was dropped; first ON remains active
        assertThat(trips).hasSize(1);
        Trip trip = trips.get(0);
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.started()).isEqualTo(t(9, 0, 0)); // first ON
    }

    // ── 6. splits_same_stop_consecutive_ons_outside_duplicate_window ─────────

    @Test
    void splits_same_stop_consecutive_ons_outside_duplicate_window() {
        // arrange: second ON at same stop, 60s later (outside 30s window)
        Tap on1  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, t(9, 1, 0), STOP1, PAN_A);
        Tap off1 = off(3, t(9, 5, 0), STOP2, PAN_A);

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
        assertThat(completed.started()).isEqualTo(t(9, 1, 0));
    }

    // ── 7. collapses_cross_stop_consecutive_ons_within_duplicate_window ───────

    @Test
    void collapses_cross_stop_consecutive_ons_within_duplicate_window() {
        // arrange: second ON at different stop, 5s later (within window)
        Tap on1  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, t(9, 0, 5), STOP2, PAN_A);
        Tap off1 = off(3, t(9, 5, 0), STOP3, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, on2, off1));

        // assert: second ON dropped; first ON active → COMPLETED from Stop1 to Stop3
        assertThat(trips).hasSize(1);
        Trip trip = trips.get(0);
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.started()).isEqualTo(t(9, 0, 0));
        assertThat(trip.fromStop()).isEqualTo(STOP1);
        assertThat(trip.toStop()).isEqualTo(STOP3);
    }

    // ── 8. splits_cross_stop_consecutive_ons_outside_duplicate_window ─────────

    @Test
    void splits_cross_stop_consecutive_ons_outside_duplicate_window() {
        // arrange: second ON at different stop, 60s later (outside window)
        Tap on1  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, t(9, 1, 0), STOP2, PAN_A);
        Tap off1 = off(3, t(9, 5, 0), STOP3, PAN_A);

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

    // ── 9. handles_double_off_by_treating_second_as_unmatched ─────────────────

    @Test
    void handles_double_off_by_treating_second_as_unmatched() {
        // arrange
        Tap on1  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap off1 = off(2, t(9, 5, 0), STOP2, PAN_A);
        Tap off2 = off(3, t(9, 6, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1, off2));

        // assert: first pair = COMPLETED, second OFF = UNMATCHED_OFF
        assertThat(trips).hasSize(2);
        long completed = trips.stream().filter(t -> t.status() == TripStatus.COMPLETED).count();
        long unmatched = trips.stream().filter(t -> t.status() == TripStatus.UNMATCHED_OFF).count();
        assertThat(completed).isEqualTo(1);
        assertThat(unmatched).isEqualTo(1);
    }

    // ── 10. pairs_taps_within_same_pan_and_bus_only ───────────────────────────

    @Test
    void pairs_taps_within_same_pan_and_bus_only() {
        // arrange: PAN_A on BUS_A taps ON; PAN_A on BUS_B taps OFF — should NOT pair
        Tap on1  = on(1, t(9, 0, 0), STOP1, PAN_A, BUS_A);
        Tap off1 = off(2, t(9, 5, 0), STOP2, PAN_A, BUS_B);

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1));

        // assert: ON → INCOMPLETE, OFF → UNMATCHED_OFF
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.INCOMPLETE).count()).isEqualTo(1);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.UNMATCHED_OFF).count()).isEqualTo(1);
    }

    // ── 11. does_not_pair_across_utc_day_boundary ─────────────────────────────

    @Test
    void does_not_pair_across_utc_day_boundary() {
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

    // ── 12. sorts_unordered_input_taps_within_bucket ──────────────────────────

    @Test
    void sorts_unordered_input_taps_within_bucket() {
        // arrange: OFF provided before ON in input list
        Tap tapOff = off(2, t(9, 5, 0), STOP2, PAN_A);
        Tap tapOn  = on(1, t(9, 0, 0), STOP1, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOff, tapOn));

        // assert: still produces a COMPLETED trip
        assertThat(trips).hasSize(1);
        assertThat(trips.get(0).status()).isEqualTo(TripStatus.COMPLETED);
    }

    // ── 13. handles_multiple_concurrent_pans_independently ───────────────────

    @Test
    void handles_multiple_concurrent_pans_independently() {
        // arrange: two PANs on same bus, interleaved taps
        Tap onA  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap onB  = on(2, t(9, 0, 30), STOP1, PAN_B);
        Tap offA = off(3, t(9, 5, 0), STOP2, PAN_A);
        Tap offB = off(4, t(9, 5, 30), STOP3, PAN_B);

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

    // ── 14. calculates_duration_in_seconds_correctly ─────────────────────────

    @Test
    void calculates_duration_in_seconds_correctly() {
        // arrange: 5 minutes 30 seconds = 330 seconds
        Tap tapOn  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap tapOff = off(2, t(9, 5, 30), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn, tapOff));

        // assert
        assertThat(trips).hasSize(1);
        assertThat(trips.get(0).durationSecs()).isEqualTo(330L);
    }

    // ── 15. preserves_company_and_bus_from_on_tap_on_emitted_trip ────────────

    @Test
    void preserves_company_and_bus_from_on_tap_on_emitted_trip() {
        // arrange: ON on BUS_A, OFF also on BUS_A (matched by bucket)
        Tap tapOn  = on(1, t(9, 0, 0), STOP1, PAN_A, BUS_A);
        Tap tapOff = off(2, t(9, 5, 0), STOP2, PAN_A, BUS_A);

        // act
        List<Trip> trips = matcher.match(List.of(tapOn, tapOff));

        // assert
        assertThat(trips).hasSize(1);
        Trip trip = trips.get(0);
        assertThat(trip.companyId()).isEqualTo(COMPANY);
        assertThat(trip.busId()).isEqualTo(BUS_A);
    }

    // ── 16. multiple_trips_per_passenger_per_day ─────────────────────────────

    @Test
    void multiple_trips_per_passenger_per_day() {
        // arrange: two complete trips for PAN_A on same day and bus
        Tap on1  = on(1, t(8, 0, 0), STOP1, PAN_A);
        Tap off1 = off(2, t(8, 5, 0), STOP2, PAN_A);
        Tap on2  = on(3, t(9, 0, 0), STOP2, PAN_A);
        Tap off2 = off(4, t(9, 10, 0), STOP3, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1, on2, off2));

        // assert
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().allMatch(t -> t.status() == TripStatus.COMPLETED)).isTrue();
    }

    // ── 17. round_trip_charges_both_legs ────────────────────────────────────

    @Test
    void round_trip_charges_both_legs() {
        // arrange: Stop1→Stop2, then Stop2→Stop1
        Tap on1  = on(1, t(8, 0, 0), STOP1, PAN_A);
        Tap off1 = off(2, t(8, 5, 0), STOP2, PAN_A);
        Tap on2  = on(3, t(9, 0, 0), STOP2, PAN_A);
        Tap off2 = off(4, t(9, 10, 0), STOP1, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1, on2, off2));

        // assert: both legs charged $3.25 (Stop1<->Stop2 pair)
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().allMatch(t -> t.chargeAmount().equals(FARE_1_2))).isTrue();
    }

    // ── 18. breaks_ties_on_identical_timestamps_using_input_id_ascending ─────

    @Test
    void breaks_ties_on_identical_timestamps_using_input_id_ascending() {
        // arrange: two ONs at identical timestamp; id=1 should be kept, id=2 dropped (within window)
        Tap on1  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, t(9, 0, 0), STOP2, PAN_A); // same timestamp, id=2
        Tap off1 = off(3, t(9, 5, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on2, on1, off1)); // deliberately unsorted

        // assert: only one trip, started from the ON with id=1 (STOP1)
        assertThat(trips).hasSize(1);
        Trip trip = trips.get(0);
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.fromStop()).isEqualTo(STOP1); // id=1 wins the tiebreak
    }

    // ── 19. new_on_after_cancelled_within_window_starts_new_trip ─────────────

    @Test
    void new_on_after_cancelled_within_window_starts_new_trip() {
        // arrange: ON+OFF same stop (CANCELLED), then new ON within 30s
        Tap on1  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap off1 = off(2, t(9, 0, 10), STOP1, PAN_A); // CANCELLED
        Tap on2  = on(3, t(9, 0, 15), STOP2, PAN_A);  // new ON within window of off1
        Tap off2 = off(4, t(9, 5, 0), STOP3, PAN_A);  // completes trip from STOP2

        // act
        List<Trip> trips = matcher.match(List.of(on1, off1, on2, off2));

        // assert: CANCELLED + COMPLETED
        assertThat(trips).hasSize(2);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.CANCELLED).count()).isEqualTo(1);
        assertThat(trips.stream().filter(t -> t.status() == TripStatus.COMPLETED).count()).isEqualTo(1);

        Trip completed = trips.stream().filter(t -> t.status() == TripStatus.COMPLETED).findFirst().orElseThrow();
        assertThat(completed.fromStop()).isEqualTo(STOP2);
    }

    // ── 20. triple_on_within_window_drops_all_but_first_on ───────────────────

    @Test
    void triple_on_within_window_drops_all_but_first_on() {
        // arrange: three ONs within 30s window; first remains active
        Tap on1  = on(1, t(9, 0, 0), STOP1, PAN_A);
        Tap on2  = on(2, t(9, 0, 5), STOP2, PAN_A);
        Tap on3  = on(3, t(9, 0, 10), STOP3, PAN_A);
        Tap off1 = off(4, t(9, 5, 0), STOP2, PAN_A);

        // act
        List<Trip> trips = matcher.match(List.of(on1, on2, on3, off1));

        // assert: only one trip — first ON (STOP1) remains active throughout
        assertThat(trips).hasSize(1);
        Trip trip = trips.get(0);
        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.fromStop()).isEqualTo(STOP1);
        assertThat(trip.toStop()).isEqualTo(STOP2);
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
            if (a.equals("Stop1") && b.equals("Stop2")) return FARE_1_2;
            if (a.equals("Stop1") && b.equals("Stop3")) return FARE_1_3;
            if (a.equals("Stop2") && b.equals("Stop3")) return FARE_2_3;
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
