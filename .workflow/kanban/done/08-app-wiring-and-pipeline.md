---
id: 08
slug: app-wiring-and-pipeline
language: java
depends-on: [04, 05, 06, 07]
parallel-safe: false
files-touched:
  - src/main/java/com/littlepay/App.java
  - src/main/java/com/littlepay/Main.java
  - src/test/java/com/littlepay/AppTest.java
acceptance:
  - "./gradlew test passes App pipeline integration scenarios"
  - "App.run() executes: load fares → read taps → match+price → sort → write"
  - "Output sorted by Started asc, then Finished asc (for UNMATCHED_OFF), then input ID asc"
  - "Empty input (header only) writes header-only output, INFO log '0 taps read'"
  - "Unknown StopId in tap data exits 5; missing input/fare file exits 2; uncaught RuntimeException exits 1"
  - "Main hand-wires every dependency (no DI framework); catches LittlepayException subclasses and maps to exit codes"
  - "Startup banner logs version, fare-table source, duplicate-window value"
failing-tests:
  - AppTest#runs_pipeline_end_to_end_against_temp_csv_files
  - AppTest#empty_input_writes_header_only_output
  - AppTest#unknown_stop_id_in_tap_data_exits_with_code_five
  - AppTest#missing_input_file_exits_with_code_two
  - AppTest#output_rows_sorted_deterministically
---

# App orchestration and Main wiring

Wires the four components from 04/05/06/07 plus the fare loader from 03 into
the end-to-end pipeline behind a single `Main` entry point. First ticket that
exercises the full process boundary with temp files.

## Scope

- `App` constructor takes `TapReader`, `TripMatcher`, `TripWriter`, `FareTable`,
  and resolved input/output paths.
- `App.run()` executes: load fares (already done before construction) → read
  taps → match+price → sort with deterministic comparator → write.
- Sort: Started asc → Finished asc (UNMATCHED_OFF rows where Started is null)
  → input tap ID asc.
- `Main.main(String[])` parses CLI, loads fares, constructs App, runs it,
  catches `LittlepayException` subclasses, maps each to its exit code, prints
  ERROR to stderr.
- Uncaught `RuntimeException` → exit code 1.
- Startup banner per design doc §10.

## References

- Design doc §3.3 Dependency injection (hand-wired in Main).
- Design doc §7.6 Output ordering.
- Design doc §9 Error handling (exit code table).
- PRD §Implementation Decisions > Modules > App, Main.
