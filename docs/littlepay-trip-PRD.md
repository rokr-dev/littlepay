# PRD: Littlepay Trip Calculator

> Slug: littlepay-trip-calculator
> Date: 2026-05-16
> Status: completed

## Problem Statement

Transit operators collect raw tap events from fare-gate readers — each event capturing a PAN, a BusID, a StopId, a TapType (ON or OFF), and a timestamp. These raw events cannot be billed or reconciled as-is. Operators need a reliable, auditable process that pairs ON and OFF taps into priced trip rows, handles missed touch-offs, flags orphaned OFFs, and produces a deterministic output file ready for downstream settlement and fare reconciliation. Without this, revenue leaks silently, support tickets multiply, and operators have no single source of truth for tap-level audit.

## Solution

A CLI batch tool that reads a `taps.csv` input file, applies a configurable fare matrix, runs a deterministic matching algorithm, and writes a `trips.csv` output file. The fare matrix is bundled by default but can be overridden at runtime. All edge cases — incomplete trips, cancelled trips, orphaned OFFs, consecutive ONs, and UTC day boundaries — are handled with explicit, auditable policies. The tool fails fast on bad input with distinct exit codes so a surrounding pipeline can branch on cause without parsing log output.

## User Stories

1. **Completed trip**: As an operator, given a PAN taps ON at Stop1 then OFF at Stop2 on the same bus on the same UTC day, I see one COMPLETED trip row charged the fare-matrix amount for the Stop1↔Stop2 pair.
2. **Incomplete trip**: As an operator, given a PAN taps ON at Stop1 with no matching OFF before end of the UTC day, I see one INCOMPLETE trip row charged the maximum fare available from Stop1 in the fare matrix, so revenue is protected and the passenger has an incentive to tap off.
3. **Cancelled trip**: As an operator, given a PAN taps ON and OFF at the same stop on the same bus, I see one CANCELLED trip row with a $0.00 charge.
4. **Unmatched OFF**: As an operator, given an OFF tap arrives with no prior ON in the current (PAN, BusID, UTC-day) bucket, I see one UNMATCHED_OFF audit row with a $0.00 charge so I can reconcile tap data against fare-gate hardware without losing the event.
5. **Multi-bus isolation**: As an operator, given PANs travelling on different buses, I see each (PAN, BusID) combination matched independently so an OFF on Bus A is never paired with an ON on Bus B.
6. **Multi-PAN isolation**: As an operator, given multiple PANs travelling on the same bus, I see each PAN's taps matched independently.
7. **UTC day boundary**: As an operator, given a PAN taps ON at 23:55 UTC and OFF at 00:05 UTC the next day, I see the ON produce an INCOMPLETE row in the first day's bucket and the OFF produce an UNMATCHED_OFF row in the second day's bucket.
8. **Duplicate-window deduplication**: As an operator, given two consecutive ON taps for the same PAN on the same bus within the configurable duplicate window (default 30 seconds), regardless of whether they are at the same or different stops, I see the second ON dropped as a hardware misfire and the first ON remain active, with a WARN log recording the drop.
9. **Consecutive ONs outside the duplicate window**: As an operator, given two consecutive ON taps separated by more than the duplicate window, I see the first ON closed as INCOMPLETE (charged max fare from its origin) and the second ON begin a new active leg.
10. **Fare-matrix override**: As an operator, I can supply `--fares /path/to/custom.csv` to use a custom fare matrix at runtime without recompiling, provided it conforms to the three-column schema.
11. **Duplicate-window override**: As an operator, I can supply `--duplicate-window-seconds N` to tune the consecutive-ON detection threshold; setting it to 0 disables deduplication entirely.
12. **Deterministic output**: As an operator running the tool twice on the same input, I get byte-for-byte identical output so golden-file comparisons and downstream diffs are stable.
13. **Fail-fast errors**: As an operator, given a malformed input file (bad header, unknown TapType, unparseable timestamp, duplicate tap ID, unknown StopId, or invalid fare amount), the tool exits immediately with a distinct exit code and a human-readable message naming the offending row or column, before producing any output.
14. **Masked PAN logs**: As an operator reviewing log output, I see PANs rendered as `****<last-four>` so log files never contain full card numbers, while the output `trips.csv` file contains raw PANs as the data product.

## Implementation Decisions

### Modules and Responsibilities

**domain** — Pure Java 21 records with no I/O or third-party imports. Contains:
- `Tap` — record holding tap id, DateTimeUTC as Instant, TapType, StopId, CompanyId, BusID, and Pan.
- `TapType` — enum: ON, OFF.
- `Trip` — record holding started, finished, durationSecs, fromStop, toStop, charge (Money), companyId, busId, pan, and TripStatus.
- `TripStatus` — enum: COMPLETED, INCOMPLETE, CANCELLED, UNMATCHED_OFF.
- `StopId` — value type wrapping a non-blank String; prevents accidental type confusion with Pan.
- `StopPair` — normalised unordered pair of StopId values; the canonical constructor lexicographically orders the two stops so Stop1↔Stop2 and Stop2↔Stop1 resolve to the same key, making fare-table lookups direction-independent.
- `Money` — value type wrapping BigDecimal (scale 2, rounding HALF_UP) and Currency; centralises the `$X.XX` formatting rule and prevents currency mixing.
- `Pan` — value type wrapping a non-blank String; exposes `masked()` which returns a constant-width four-asterisk prefix plus the last four digits regardless of PAN length (preventing PAN-length leakage in logs).

**pricing** — Contains:
- `FareTable` interface — lookups: `fareFor(StopPair)` and `maxFareFrom(StopId)`.
- `FareTableImpl` — immutable map-backed implementation; `maxFareFrom` is precomputed at construction time.
- `FareTableLoader` — loads and strictly validates `fares.csv`; enforces the three-column schema, rejects negative or zero amounts, rejects duplicate pairs in either direction, rejects header drift, and fails fast with exit code 6 on any violation.

**matching** — Contains:
- `TripMatcher` interface — accepts a list of Tap records and returns a list of Trip records.
- `StateMachineTripMatcher` — per (PAN, companyId, BusID, UTC-day) bucket state machine. States: `waiting` (no active ON) and `onHeld(prev)` (holding a prior ON). Transitions:
  - ON arriving in `waiting` → enter `onHeld(tap)`.
  - ON arriving in `onHeld(prev)`:
    - If gap ≤ duplicate-window → drop the new ON as a duplicate; emit WARN log; remain in `onHeld(prev)`.
    - If gap > duplicate-window → emit prev as INCOMPLETE (max fare from prev.stop); enter `onHeld(tap)`.
  - OFF arriving in `waiting` → emit UNMATCHED_OFF.
  - OFF arriving in `onHeld(prev)`:
    - Same stop → emit CANCELLED ($0.00); enter `waiting`.
    - Different stop → emit COMPLETED (fare-matrix charge); enter `waiting`.
  - End of bucket with `onHeld(prev)` active → emit prev as INCOMPLETE.
  - The duplicate-window rule is applied symmetrically to same-stop and cross-stop consecutive ONs.
  - Input taps within each bucket are sorted by DateTimeUTC ascending, with tap ID ascending as a deterministic tiebreaker for identical timestamps.

**io** — Adapters at the boundary. Contains:
- `TapReader` port and `CsvTapReader` adapter — reads `taps.csv` using Apache Commons CSV; strips UTF-8 BOM if present before header validation; strips whitespace from all fields; skips blank lines; validates header strictly; parses timestamps with `DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")` and UTC zone; parses TapType case-insensitively; rejects duplicate tap IDs; fails fast on any parse error with exit code 3 (header mismatch) or 4 (malformed row / duplicate ID).
- `TripWriter` port and `CsvTripWriter` adapter — writes `trips.csv` using Apache Commons CSV; formats timestamps as `dd-MM-yyyy HH:mm:ss` UTC; formats ChargeAmount as `$X.XX`; emits space after each comma to match the brief sample; writes a space for empty numeric fields (DurationSecs absent on INCOMPLETE and UNMATCHED_OFF); overwrites any existing output file with a WARN log; creates the output directory if missing. Both reader and writer use UTF-8 without BOM and RFC 4180 quoting.
- `FareTableLoader` — described under pricing above.

**cli** — Hand-rolled argument parser (~15 lines). Parses two positional arguments (input path, output path), the optional `--fares <path>` flag, and the optional `--duplicate-window-seconds <N>` flag. Prints usage to stdout and exits 0 on `-h` / `--help`. Prints usage to stderr and exits 2 on no arguments.

**App** — Orchestrates the pipeline: load fare table → read taps → match and price → sort output → write trips. Receives all dependencies as constructor arguments.

**Main** — Entry point. Wires all dependencies by hand (no DI framework), invokes App, catches `LittlepayException` subclasses, maps each to its exit code, and prints the error message to stderr.

### Architectural Decisions

**Hexagonal-lite** — Pure domain with no I/O imports; ports (interfaces) at the boundary; CSV adapters as the only I/O-touching code. Dependencies point inward only. No use-case classes, no application service layer, no domain-event bus — none are justified by a single-command CLI batch tool.

**No DI framework** — The full wiring graph fits in ~10 lines of constructor injection in Main. Spring, Guice, and Dagger are anti-signals at this project size.

**BigDecimal Money** — Binary floating point cannot represent monetary amounts exactly. Money is always represented as BigDecimal (scale 2, rounding HALF_UP) wrapped in the Money value type.

**Normalised StopPair** — Fare-table symmetry is encoded in the key, not by storing duplicate rows. One row per unordered pair; the normalised StopPair canonical constructor enforces ordering.

**UTC calendar-day bucket** — Matches the `DateTimeUTC` column name; gives a locale-independent, unambiguous cutoff. Service-day boundary is explicitly out of scope.

**(PAN, BusID) matching key** — PAN alone would incorrectly pair an OFF on Bus A with an ON on Bus B. BusID implicitly scopes the company; CompanyId is redundant for matching but is emitted in Trip output from the ON tap.

**Amendment (post-impl):** CompanyId was added to the bucket key (commit `e59c6b8`) to defend against BusID reuse across companies. The key is now `(PAN, companyId, busId, UTC-day)`.

**Fail-fast exit codes** — Distinct exit codes per failure class:

| Failure class | Exit code |
|---|---|
| Input or fare file missing / unreadable | 2 |
| Tap CSV header mismatch | 3 |
| Tap CSV malformed row, bad timestamp, unknown TapType, duplicate ID | 4 |
| Unknown StopId in tap data (not in fare table) | 5 |
| Fare table validation failure | 6 |
| Uncaught RuntimeException | 1 |

**Deterministic output ordering** — Primary key: Started ascending. For UNMATCHED_OFF rows (no Started): Finished ascending. Tiebreaker: input tap ID ascending. Guarantees stable golden-file E2E test output.

**Locale.ROOT pinning** — All DateTimeFormatter and BigDecimal formatting calls explicitly use Locale.ROOT to prevent locale-dependent drift on reviewer machines.

**AUD hard-coded** — The fare matrix uses `$` notation and Littlepay is an Australian company. Multi-currency is the Joda-Money / JSR-354 upgrade path.

### Input Schema (taps.csv)

Columns: `ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN`

Timestamp format: `dd-MM-yyyy HH:mm:ss` UTC. TapType: `ON` or `OFF` (case-insensitive). UTF-8, BOM-tolerant, RFC 4180.

### Output Schema (trips.csv)

Columns: `Started, Finished, DurationSecs, FromStopId, ToStopId, ChargeAmount, CompanyId, BusID, PAN, Status`

Timestamp format: `dd-MM-yyyy HH:mm:ss` UTC. ChargeAmount format: `$X.XX` always including $0.00. Status: one of `COMPLETED`, `INCOMPLETE`, `CANCELLED`, `UNMATCHED_OFF`. Space after each comma.

### Fare Matrix Schema (fares.csv)

Three columns: `FromStopId,ToStopId,Amount`. Amount must match `^\d+\.\d{2}$` exactly — digits, a period, and exactly two decimal digits. No currency symbol, no thousand separators, no scientific notation. One row per unordered stop pair; symmetry is handled by the normalised StopPair key. Header must match exactly; any drift fails fast with exit code 6.

## Testing Decisions

Testing targets external behaviour, not internal implementation. The pyramid has four layers:

**Golden E2E (1 test)** — Runs App end-to-end against the verbatim specification sample input (`taps-sample.csv`) and compares actual output line-by-line against a hand-derived expected file (`trips-sample-expected.csv`) committed to the repo. Also serves as the deliverable "output file for the example input".

**Integration CSV roundtrip (~6 tests)** — CsvTapReader and CsvTripWriter tests covering: BOM-prefixed input accepted, CRLF and LF both accepted, trailing whitespace stripped, blank lines skipped, header mismatch rejected (exit code 3), malformed row rejected (exit code 4), duplicate ID rejected (exit code 4), unknown StopId rejected (exit code 5), round-trip write/read produces equivalent Trip records.

**Domain logic — matcher + pricer (~25 + ~15 tests)** — See named scenario lists below.

**Value-type contracts (~15 tests)** — Money, StopPair, Pan, StopId, Tap compact-constructor invariants.

### Framework

JUnit 5 (`org.junit.jupiter`) with AssertJ for fluent assertions. `@Nested` to group scenarios by behaviour. `@DisplayName` with sentence-form names. `@ParameterizedTest` + `@CsvSource` for fare-matrix symmetry and duplicate-window boundary thresholds. No mocking framework — domain is pure; ports use hand-rolled fakes.

### Matcher Scenarios (23)

1. `completes_trip_when_on_and_off_at_different_stops`
2. `cancels_trip_when_on_and_off_at_same_stop`
3. `marks_trip_incomplete_when_on_has_no_off_before_eod`
4. `emits_unmatched_off_when_off_arrives_with_no_prior_on`
5. `dedups_same_stop_consecutive_ons_within_duplicate_window`
6. `splits_same_stop_consecutive_ons_outside_duplicate_window`
7. `collapses_cross_stop_consecutive_ons_within_duplicate_window`
8. `splits_cross_stop_consecutive_ons_outside_duplicate_window`
9. `handles_double_off_by_treating_second_as_unmatched`
10. `pairs_taps_within_same_pan_and_bus_only`
11. `does_not_pair_across_utc_day_boundary`
12. `sorts_unordered_input_taps_within_bucket`
13. `handles_multiple_concurrent_pans_independently`
14. `calculates_duration_in_seconds_correctly`
15. `preserves_company_and_bus_from_on_tap_on_emitted_trip`
16. `multiple_trips_per_passenger_per_day`
17. `round_trip_charges_both_legs`
18. `breaks_ties_on_identical_timestamps_using_input_id_ascending`
19. `new_on_after_cancelled_within_window_starts_new_trip`
20. `triple_on_within_window_drops_all_but_first_on`
21. `tap_type_parses_case_insensitively`
22. `input_with_utf8_bom_is_accepted_and_header_validated_after_strip`
23. `input_with_trailing_whitespace_in_fields_is_tolerated`

### Pricing Scenarios (15)

1. `charges_completed_trip_per_fare_table`
2. `treats_fare_pair_as_unordered`
3. `charges_incomplete_trip_max_from_origin_stop`
4. `charges_cancelled_trip_zero`
5. `charges_unmatched_off_zero`
6. `throws_unknown_stop_when_pair_not_in_table`
7. `loader_rejects_negative_fare`
8. `loader_rejects_zero_fare`
9. `loader_rejects_duplicate_pair_in_either_direction`
10. `loader_rejects_header_drift`
11. `loader_rejects_amount_with_currency_symbol`
12. `loader_rejects_amount_with_thousands_separator`
13. `loader_rejects_amount_with_more_than_two_decimal_places`
14. `loader_rejects_amount_in_scientific_notation`
15. `loader_rejects_amount_with_no_decimal_places`

### Value-Type Scenarios

- **Money**: scale enforced at construction, HALF_UP rounding, currency mismatch rejected, equals/hashCode contract, `format()` output matches `$X.XX`.
- **StopPair**: normalisation (both orderings produce equal instances), equals/hashCode contract.
- **Pan**: `masked()` produces constant-width four-asterisk prefix plus last four digits for Amex (15 digits), Visa/Mastercard (16 digits), and Maestro (19 digits) — all 8-character output; blank value rejected; equals/hashCode contract.
- **StopId**: blank value rejected; equals/hashCode contract.

### Golden E2E

Input is the verbatim specification sample `taps-sample.csv`. Expected output `trips-sample-expected.csv` is hand-derived from the spec and committed to the repository. The test runs App end-to-end, writes to a temp file, and compares line-by-line. This file is duplicated under `examples/` for reviewer convenience — two physical files rather than a symlink to ensure compatibility on Windows.

### Explicit Non-Goals for Testing

Property-based testing (jqwik) and mutation testing (Pitest) are not included. No code-coverage gate — coverage measurement is fine; gating on a magic number is gaming. Fixed `Instant` values in all fixtures; no `Thread.sleep` or real-clock dependencies. No shared mutable state between tests.

## Out of Scope

- `--fare-mode favour-passenger`: opt-in operator-policy mode to cap multi-leg charges at the direct end-to-end fare when one exists. Per-leg pricing is the industry default (Opal, Myki, TfL Oyster, EZ-Link, Octopus); operator commercial policy is not specified in the brief.
- Service-day boundary: configurable cutoff (e.g., 03:00 to 03:00) aligned with transit operations. The brief uses UTC timestamps and gives no service-day signal.
- Streaming I/O for multi-GB inputs: noted as the refactor path in README; out of scope per bounded-input assumption.
- Property-based testing and mutation testing.
- Multi-currency support: Joda-Money / JSR-354 is the upgrade path.
- DI framework (Spring, Guice, Dagger).
- CLI framework (Picocli, Commons-CLI).
- Lombok.
- Spring / Spring Boot.

## Further Notes

- AUD is hard-coded; the Money value type is designed to accept Currency so multi-currency is a future upgrade without a type redesign.
- `Locale.ROOT` is pinned everywhere that formats dates or numbers to prevent reviewer-machine locale drift.
- PAN masking in logs is log hygiene only, not full PCI-DSS scope. The output `trips.csv` contains raw PANs because it is the data product. Full PCI scope would bring additional storage, transmission, and retention controls outside this exercise.
