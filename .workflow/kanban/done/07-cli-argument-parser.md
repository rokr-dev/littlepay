---
id: 07
slug: cli-argument-parser
language: java
depends-on: [03]
parallel-safe: true
files-touched:
  - src/main/java/com/littlepay/cli/Cli.java
  - src/main/java/com/littlepay/cli/CliArgs.java
  - src/test/java/com/littlepay/cli/CliTest.java
acceptance:
  - "./gradlew test passes CLI scenarios"
  - "Two positional args (input, output) parsed into CliArgs"
  - "--fares <path> sets a custom fare file path; default points to bundled fares.csv"
  - "--duplicate-window-seconds <N> sets the matcher window; default 30"
  - "-h / --help prints usage to stdout and exits 0"
  - "No arguments prints usage to stderr and exits 2"
  - "Hand-rolled parser, no third-party CLI dependency"
failing-tests:
  - CliTest#parses_two_positional_arguments
  - CliTest#parses_fares_flag_override
  - CliTest#parses_duplicate_window_seconds_flag
  - CliTest#help_flag_prints_usage_to_stdout_and_exits_zero
  - CliTest#no_arguments_prints_usage_to_stderr_and_exits_two
---

# Hand-rolled CLI argument parser

Tracer slice from `String[] args` → `CliArgs` (positional paths plus parsed
flags). ~15 lines of parsing per design doc §8.2. Parallel-safe with 04/05/06:
disjoint package, disjoint files.

## Scope

- `Cli.parse(String[])` returns a `CliArgs` record.
- Positional: input path, output path.
- Flags: `--fares <path>`, `--duplicate-window-seconds <N>`.
- `-h` / `--help` prints usage to stdout, returns a sentinel (Main exits 0).
- Empty args prints usage to stderr, returns a sentinel (Main exits 2).
- No third-party CLI library.

## References

- Design doc §8 CLI (argument shape, no framework).
- PRD §Implementation Decisions > Modules > cli.
