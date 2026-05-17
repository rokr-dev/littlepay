---
id: 03
slug: fare-table-load-and-validate
language: java
depends-on: [02]
parallel-safe: false
support: true
files-touched:
  - src/main/java/com/littlepay/pricing/FareTable.java
  - src/main/java/com/littlepay/pricing/FareTableImpl.java
  - src/main/java/com/littlepay/io/FareTableLoader.java
  - src/main/java/com/littlepay/LittlepayException.java
  - src/main/java/com/littlepay/InputFileException.java
  - src/main/java/com/littlepay/TapHeaderException.java
  - src/main/java/com/littlepay/TapRowException.java
  - src/main/java/com/littlepay/UnknownStopException.java
  - src/main/java/com/littlepay/FareTableException.java
  - src/test/java/com/littlepay/pricing/FareTableTest.java
  - src/test/java/com/littlepay/io/FareTableLoaderTest.java
  - src/test/resources/fares-sample.csv
acceptance:
  - "./gradlew test passes all 15 pricing scenarios from PRD"
  - "FareTable lookup treats StopPair as unordered (symmetry test)"
  - "maxFareFrom(stop) returns the largest fare touching that stop"
  - "Loader rejects: negative, zero, currency-symbol, thousands-separator, >2dp, scientific notation, no decimals, duplicate pair (either order), header drift — all with exit code 6"
  - "Unknown StopPair lookup throws an exception that maps to exit code 5"
failing-tests:
  - FareTableTest#charges_completed_trip_per_fare_table
  - FareTableTest#treats_fare_pair_as_unordered
  - FareTableTest#max_fare_from_origin_stop_returns_largest_touching_fare
  - FareTableTest#throws_unknown_stop_when_pair_not_in_table
  - FareTableLoaderTest#loader_rejects_negative_fare
  - FareTableLoaderTest#loader_rejects_zero_fare
  - FareTableLoaderTest#loader_rejects_duplicate_pair_in_either_direction
  - FareTableLoaderTest#loader_rejects_header_drift
  - FareTableLoaderTest#loader_rejects_amount_with_currency_symbol
  - FareTableLoaderTest#loader_rejects_amount_with_thousands_separator
  - FareTableLoaderTest#loader_rejects_amount_with_more_than_two_decimal_places
  - FareTableLoaderTest#loader_rejects_amount_in_scientific_notation
  - FareTableLoaderTest#loader_rejects_amount_with_no_decimal_places
---

# Fare table loader, validator, and lookup

Tracer slice: CSV file on disk → validated immutable FareTable → lookup +
maxFareFrom usable by the pricer. Includes the project's exception hierarchy
because every loader rejection has to map cleanly to a documented exit code.

## Scope

- `FareTable` interface: `fareFor(StopPair)`, `maxFareFrom(StopId)`.
- `FareTableImpl`: immutable map-backed; `maxFareFrom` precomputed at
  construction.
- `FareTableLoader`: strict header check, positive-amount check, duplicate-pair
  check (either direction), and the seven amount-format rejections from the
  PRD pricing scenarios.
- `LittlepayException` base + five subclasses, each carrying its exit code
  (2/3/4/5/6) per design doc §9.2.

## References

- Design doc §6 Fare matrix (schema, normalisation, load-time validation).
- Design doc §9 Error handling (exception hierarchy, exit codes).
- PRD §Testing Decisions > Pricing Scenarios (15 scenarios).
