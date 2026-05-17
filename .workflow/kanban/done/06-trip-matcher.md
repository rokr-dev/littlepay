---
id: 06
slug: trip-matcher
language: java
depends-on: [02]
parallel-safe: true
files-touched:
  - src/main/java/com/littlepay/matching/TripMatcher.java
  - src/main/java/com/littlepay/matching/StateMachineTripMatcher.java
  - src/test/java/com/littlepay/matching/StateMachineTripMatcherTest.java
acceptance:
  - "./gradlew test passes all 23 matcher scenarios from PRD §Matcher Scenarios"
  - "Default duplicate-window 30 seconds; configurable via constructor argument"
  - "(PAN, BusID) is the matching key; UTC calendar-day is the bucket"
  - "Within-window consecutive ONs (same OR cross stop) drop the second; outside-window split into INCOMPLETE + new active leg"
  - "Triple-ON within window keeps only the first ON active"
  - "Tie-breaking on identical timestamps uses input tap ID ascending"
  - "Pricer interface used; UNMATCHED_OFF and CANCELLED trips charged zero"
failing-tests:
  - StateMachineTripMatcherTest#completes_trip_when_on_and_off_at_different_stops
  - StateMachineTripMatcherTest#cancels_trip_when_on_and_off_at_same_stop
  - StateMachineTripMatcherTest#marks_trip_incomplete_when_on_has_no_off_before_eod
  - StateMachineTripMatcherTest#emits_unmatched_off_when_off_arrives_with_no_prior_on
  - StateMachineTripMatcherTest#dedups_same_stop_consecutive_ons_within_duplicate_window
  - StateMachineTripMatcherTest#splits_same_stop_consecutive_ons_outside_duplicate_window
  - StateMachineTripMatcherTest#collapses_cross_stop_consecutive_ons_within_duplicate_window
  - StateMachineTripMatcherTest#splits_cross_stop_consecutive_ons_outside_duplicate_window
  - StateMachineTripMatcherTest#handles_double_off_by_treating_second_as_unmatched
  - StateMachineTripMatcherTest#pairs_taps_within_same_pan_and_bus_only
  - StateMachineTripMatcherTest#does_not_pair_across_utc_day_boundary
  - StateMachineTripMatcherTest#sorts_unordered_input_taps_within_bucket
  - StateMachineTripMatcherTest#handles_multiple_concurrent_pans_independently
  - StateMachineTripMatcherTest#calculates_duration_in_seconds_correctly
  - StateMachineTripMatcherTest#preserves_company_and_bus_from_on_tap_on_emitted_trip
  - StateMachineTripMatcherTest#multiple_trips_per_passenger_per_day
  - StateMachineTripMatcherTest#round_trip_charges_both_legs
  - StateMachineTripMatcherTest#breaks_ties_on_identical_timestamps_using_input_id_ascending
  - StateMachineTripMatcherTest#new_on_after_cancelled_within_window_starts_new_trip
  - StateMachineTripMatcherTest#triple_on_within_window_drops_all_but_first_on
---

# Trip matcher and pricer integration

Pure-domain tracer slice: `List<Tap>` → `List<Trip>` with statuses and charges
assigned. The matcher calls the `FareTable` from ticket 03 to compute charges
inline so the same pass produces priced trips ready for the writer.

## Scope

- `TripMatcher` port; `StateMachineTripMatcher` implementation.
- Bucket by (PAN, BusID, UTC calendar day); sort each bucket by (timestamp asc,
  input ID asc) before processing.
- State machine handles: ON → OFF (different stop) = COMPLETED; ON → OFF (same
  stop) = CANCELLED; ON with no OFF = INCOMPLETE; OFF with no prior ON =
  UNMATCHED_OFF.
- Consecutive-ONs Read-H rule: configurable duplicate window (default 30s),
  applied symmetrically to same-stop and cross-stop ONs.
- Triple-ON within window: drops all but the first.
- Pricing folded in: COMPLETED → `fareFor(StopPair)`; INCOMPLETE →
  `maxFareFrom(originStop)`; CANCELLED / UNMATCHED_OFF → zero.

## References

- Design doc §4 Domain rules (matching key, ordering, statuses, Read H, day boundary).
- PRD §Testing Decisions > Matcher Scenarios (23 scenarios).
