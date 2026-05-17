---
id: 09
slug: golden-e2e-test-and-examples
language: java
depends-on: [08]
parallel-safe: false
files-touched:
  - src/main/resources/fares.csv
  - src/test/resources/taps-sample.csv
  - src/test/resources/trips-sample-expected.csv
  - examples/taps-sample.csv
  - examples/trips-sample-expected.csv
  - src/test/java/com/littlepay/GoldenE2ETest.java
acceptance:
  - "./gradlew test passes GoldenE2ETest"
  - "GoldenE2ETest invokes App against taps-sample.csv, writes to temp file, compares line-by-line to trips-sample-expected.csv with zero diffs"
  - "examples/taps-sample.csv and examples/trips-sample-expected.csv exist as physical copies (not symlinks) and are byte-identical to the src/test/resources versions"
  - "Default fares.csv ships in src/main/resources with the three-stop fare matrix: Stop1↔Stop2 $3.25, Stop1↔Stop3 $7.30, Stop2↔Stop3 $5.50"
  - "java -jar build/libs/littlepay.jar examples/taps-sample.csv /tmp/littlepay-out.csv exits 0 and writes output byte-equivalent to examples/trips-sample-expected.csv"
failing-tests:
  - GoldenE2ETest#sample_input_produces_expected_output_line_by_line
  - GoldenE2ETest#examples_copies_match_test_resources
---

# Golden end-to-end test and reviewer example bundle

Final functional ticket. Locks the spec sample input + hand-derived expected
output into a single test that runs the whole binary. Doubles as the
deliverable "output file for the example input" called out in the design doc.

## Scope

- `src/test/resources/taps-sample.csv`: verbatim spec sample.
- `src/test/resources/trips-sample-expected.csv`: hand-derived from the spec.
- `examples/` copies for reviewer convenience (physical files, not symlinks —
  Windows compatibility).
- `src/main/resources/fares.csv`: default fare matrix shipping with the JAR.
- `GoldenE2ETest`: invokes `App` against the sample, writes to a temp file,
  asserts line-by-line equality with the expected file.
- A second test asserts the `examples/` files match the `src/test/resources/`
  files byte-for-byte.

## References

- Design doc §6.2 Default location and override (bundled fares.csv).
- Design doc §12.7 Golden E2E.
- PRD §Testing Decisions > Golden E2E.
- Design doc §13 Deliverables.
