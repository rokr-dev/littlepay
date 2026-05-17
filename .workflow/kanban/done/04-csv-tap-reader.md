---
id: 04
slug: csv-tap-reader
language: java
depends-on: [02, 03]
parallel-safe: true
files-touched:
  - src/main/java/com/littlepay/io/TapReader.java
  - src/main/java/com/littlepay/io/CsvTapReader.java
  - src/test/java/com/littlepay/io/CsvTapReaderTest.java
  - src/test/resources/taps-bom.csv
  - src/test/resources/taps-crlf.csv
  - src/test/resources/taps-bad-header.csv
  - src/test/resources/taps-bad-type.csv
  - src/test/resources/taps-duplicate-id.csv
  - src/test/resources/taps-whitespace.csv
acceptance:
  - "./gradlew test passes CsvTapReader integration scenarios"
  - "UTF-8 BOM stripped before header validation"
  - "CRLF and LF both accepted (Commons CSV transparent)"
  - "Trailing whitespace in fields tolerated"
  - "Blank lines skipped"
  - "Header mismatch throws TapHeaderException (exit code 3)"
  - "Bad TapType, bad timestamp, duplicate ID each throw TapRowException (exit code 4) with row-number message"
  - "Timestamp parsed with dd-MM-yyyy HH:mm:ss at UTC; TapType parsed case-insensitively"
failing-tests:
  - CsvTapReaderTest#input_with_utf8_bom_is_accepted_and_header_validated_after_strip
  - CsvTapReaderTest#input_with_crlf_line_endings_is_accepted
  - CsvTapReaderTest#input_with_trailing_whitespace_in_fields_is_tolerated
  - CsvTapReaderTest#blank_lines_skipped
  - CsvTapReaderTest#header_mismatch_throws_with_exit_code_three
  - CsvTapReaderTest#bad_tap_type_throws_with_exit_code_four
  - CsvTapReaderTest#duplicate_id_throws_with_exit_code_four
  - CsvTapReaderTest#tap_type_parses_case_insensitively
---

# CsvTapReader adapter

Tracer slice from CSV file on disk → `List<Tap>` with full parse and validation.
Parallel-safe with 05/06/07: disjoint package, disjoint files.

## Scope

- `TapReader` port; `CsvTapReader` adapter using Apache Commons CSV.
- BOM strip (`EF BB BF`) before header validation.
- Header schema strictly validated: `ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN`.
- Per-field `.strip()`; blank lines skipped with DEBUG log.
- Timestamp: `DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(ZoneOffset.UTC)`.
- `TapType` parsed case-insensitively after strip; anything else fails fast.
- Duplicate `ID` across rows fails fast with row number in message.
- `Locale.ROOT` pinned for all parsing.

## References

- Design doc §7.2 Input format and §7.4 Encoding.
- PRD §Testing Decisions > Matcher Scenarios items 21-23 (case-insensitive, BOM, whitespace).
