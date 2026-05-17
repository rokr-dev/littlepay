---
id: 10
slug: docs-assumptions-and-readme
language: markdown
depends-on: [01, 02, 03, 04, 05, 06, 07, 08, 09]
parallel-safe: false
files-touched:
  - ASSUMPTIONS.md
  - README.md
acceptance:
  - "ASSUMPTIONS.md exists at repo root and contains H2 sections (verifiable by grep): 'Matching key', 'Duplicate detection window', 'Calendar-day boundary', 'Incomplete trip charge', 'UNMATCHED_OFF audit row', 'AUD hard-coded', 'Fare matrix symmetry', 'PAN masking scope'"
  - "README.md exists at repo root and contains H2 sections (verifiable by grep): 'Prerequisites', 'Build', 'Run', 'Test', 'Example', 'AI assistance disclosure'"
  - "README 'Build' section contains the exact command './gradlew jar' in a fenced code block"
  - "README 'Test' section contains the exact command './gradlew test' in a fenced code block"
  - "README 'Run' section contains a 'java -jar build/libs/littlepay.jar <input.csv> <output.csv> [--fares <path>] [--duplicate-window-seconds <N>]' invocation in a fenced code block"
  - "README 'Example' section contains a runnable command using examples/taps-sample.csv that, when executed verbatim after './gradlew jar', exits 0 and produces output byte-equivalent to examples/trips-sample-expected.csv"
failing-tests: []
---

# ASSUMPTIONS.md and README.md

Documentation-only ticket. Runs last so it can document the actual binary,
flags, and example behaviour produced by tickets 01–09.

## Scope

- `ASSUMPTIONS.md`: one section per assumption recorded during implementation.
  Cross-reference the relevant design doc sections without copying them.
- `README.md`: reviewer-facing build/run/test guide. Single-page, terse, with
  copy-pasteable commands. Includes the AI assistance disclosure paragraph
  called out in PRD §Further Notes.

## Acceptance verification

Each acceptance criterion is mechanically checkable:

- Section presence: `grep -E '^## Section Name' README.md`.
- Command presence: `grep -F './gradlew jar' README.md`.
- Runnable command: execute the example command verbatim and `diff` the output
  against `examples/trips-sample-expected.csv` — must produce zero diffs.

## References

- Design doc §13 Deliverables (ASSUMPTIONS and README content).
- PRD §Further Notes (AI assistance disclosure wording).
