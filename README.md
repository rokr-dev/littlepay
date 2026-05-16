# Littlepay Trip Calculator

Reads a CSV of transit tap events, pairs ON/OFF taps into priced trips, and writes a deterministic output CSV ready for fare settlement.

## Prerequisites

- Java 21 (JDK, not JRE)

```
java --version   # should report 21.x
```

No other runtime dependencies are required. The fat JAR bundles all libraries.

## Build

```
./gradlew jar
```

Produces `build/libs/littlepay.jar`.

## Run

```
java -jar build/libs/littlepay.jar <input.csv> <output.csv> [--fares <path>] [--duplicate-window-seconds <N>]
```

| Argument | Required | Description |
|---|---|---|
| `<input.csv>` | yes | Path to the input taps CSV |
| `<output.csv>` | yes | Path to write the output trips CSV |
| `--fares <path>` | no | Override the bundled fare matrix (default: `fares.csv` next to the JAR) |
| `--duplicate-window-seconds <N>` | no | Consecutive-ON dedup window in seconds (default: 30; 0 disables) |

Pass `-h` or `--help` to print usage and exit 0. Running with no arguments prints usage to stderr and exits 2.

## Test

```
./gradlew test
```

Runs the full test suite (unit + golden-file E2E). All tests must pass before the binary is considered valid.

## Example

After building, run the bundled sample:

```
java -jar build/libs/littlepay.jar examples/taps-sample.csv /tmp/trips-out.csv
diff /tmp/trips-out.csv examples/trips-sample-expected.csv
```

The `diff` should produce no output — the generated file is byte-equivalent to the committed expected output.

The `examples/` directory contains:
- `taps-sample.csv` — nine tap events covering all trip statuses
- `trips-sample-expected.csv` — the corresponding expected output

## Design overview

The tool follows a hexagonal-lite architecture:

```
cli → App → [ FareTableLoader | CsvTapReader ] → TripMatcher → Pricer → CsvTripWriter
```

- **CsvTapReader** — parses and validates tap rows; rejects malformed input fast.
- **TripMatcher** — groups taps into `(PAN, BusID, UTC-day)` buckets, applies the duplicate-detection window, and produces trips with statuses: COMPLETED, INCOMPLETE, CANCELLED, UNMATCHED_OFF.
- **Pricer** — looks up fares from the fare matrix; charges max-from-origin for INCOMPLETE trips; zero for CANCELLED and UNMATCHED_OFF.
- **CsvTripWriter** — writes the output CSV sorted by Started timestamp (then input ID as tiebreaker).

See `ASSUMPTIONS.md` for the full list of domain assumptions and edge-case policies.

## Trip statuses

| Status | Meaning | Charge |
|---|---|---|
| COMPLETED | ON + OFF at different stops | Fare table lookup |
| CANCELLED | ON + OFF at same stop | $0.00 |
| INCOMPLETE | ON tap with no matching OFF by end of UTC day | Max fare from origin stop |
| UNMATCHED_OFF | OFF tap with no prior ON in the same bucket | $0.00 (audit row) |

## Project layout

```
src/main/java/com/littlepay/   — production code
src/test/java/com/littlepay/   — unit and integration tests
src/test/resources/            — golden-file test data
examples/                      — sample input and expected output for manual verification
fares.csv                      — default fare matrix (bundled in JAR working directory)
ASSUMPTIONS.md                 — full list of domain assumptions
```

## AI assistance disclosure

Architecture, design decisions, and edge-case policies were developed by the author in a planning session before any code was written. Code and test scaffolding were AI-generated and reviewed by the author before commit.
