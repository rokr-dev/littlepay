---
id: 05
slug: csv-trip-writer
language: java
depends-on: [02]
parallel-safe: true
files-touched:
  - src/main/java/com/littlepay/io/TripWriter.java
  - src/main/java/com/littlepay/io/CsvTripWriter.java
  - src/test/java/com/littlepay/io/CsvTripWriterTest.java
acceptance:
  - "./gradlew test passes CsvTripWriter scenarios"
  - "Output header row matches design doc §7.3 exactly"
  - "UNMATCHED_OFF rows emit empty cells for null Started/FromStopId fields"
  - "Round-trip (write then re-read) reproduces equivalent Trip records"
  - "Output directory created via Files.createDirectories if missing"
  - "Existing output file overwritten; WARN log line recorded"
  - "All timestamp and Money formatting uses Locale.ROOT"
failing-tests:
  - CsvTripWriterTest#emits_expected_header_row
  - CsvTripWriterTest#unmatched_off_row_emits_empty_started_and_from_stop_cells
  - CsvTripWriterTest#round_trip_write_then_read_produces_equivalent_trips
  - CsvTripWriterTest#creates_output_directory_when_missing
  - CsvTripWriterTest#overwrites_existing_output_file_and_logs_warn
---

# CsvTripWriter adapter

Tracer slice from `List<Trip>` → CSV file on disk. Parallel-safe with 04/06/07:
disjoint files; only shared dependency is the already-frozen domain package
from ticket 02.

## Scope

- `TripWriter` port; `CsvTripWriter` adapter using Apache Commons CSV.
- Header row exactly per design doc §7.3.
- Money formatted as `$X.XX` via `Money.format()`.
- Timestamps formatted with `dd-MM-yyyy HH:mm:ss` at UTC, `Locale.ROOT`.
- Null `Started` / `FromStopId` on UNMATCHED_OFF rows emit empty cells (not the
  literal string `null`).
- `Files.createDirectories` for the parent path.
- Overwrite existing file; WARN log on overwrite.

## References

- Design doc §7.3 Output format, §7.5 File handling, §7.6 Output ordering.
- Design doc §10 Logging (WARN on overwrite).
