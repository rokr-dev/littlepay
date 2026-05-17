---
id: 02
slug: domain-value-types
language: java
depends-on: [01]
parallel-safe: false
support: true
files-touched:
  - src/main/java/com/littlepay/domain/StopId.java
  - src/main/java/com/littlepay/domain/StopPair.java
  - src/main/java/com/littlepay/domain/Money.java
  - src/main/java/com/littlepay/domain/pan/Pan.java
  - src/main/java/com/littlepay/domain/TapType.java
  - src/main/java/com/littlepay/domain/Tap.java
  - src/main/java/com/littlepay/domain/Trip.java
  - src/main/java/com/littlepay/domain/TripStatus.java
  - src/test/java/com/littlepay/domain/MoneyTest.java
  - src/test/java/com/littlepay/domain/StopPairTest.java
  - src/test/java/com/littlepay/domain/StopIdTest.java
  - src/test/java/com/littlepay/domain/pan/PanTest.java
acceptance:
  - "./gradlew test passes all value-type contract tests"
  - "Money enforces scale 2 with HALF_UP at construction, rejects currency mismatch, format() emits $X.XX"
  - "StopPair normalises so (A,B) equals (B,A); equals/hashCode contract holds"
  - "Pan.masked() emits a constant-width 8-char string (four asterisks + last 4) for 15/16/19-digit inputs"
  - "StopId rejects blank input"
failing-tests:
  - MoneyTest#scale_enforced_at_construction
  - MoneyTest#rounds_half_up
  - MoneyTest#rejects_currency_mismatch_on_arithmetic
  - MoneyTest#format_emits_dollar_xx_xx_locale_root
  - StopPairTest#normalises_unordered_pair
  - StopPairTest#equals_and_hashCode_contract
  - PanTest#masked_amex_15_digits_is_eight_chars
  - PanTest#masked_visa_16_digits_is_eight_chars
  - PanTest#masked_maestro_19_digits_is_eight_chars
  - PanTest#rejects_blank_pan
  - StopIdTest#rejects_blank_stop_id
---

# Domain value types

Tracer slice: every value type plus its construction-time invariants, proven
by JUnit 5 + AssertJ tests. No I/O, no third-party imports.

## Scope

- Java records with compact constructors for non-null and shape enforcement.
- `Money` wraps `BigDecimal` (scale 2, HALF_UP) + `Currency`; arithmetic with
  mismatched currencies throws.
- `StopPair` normalises the unordered pair in its canonical constructor so
  `new StopPair(a, b).equals(new StopPair(b, a))`.
- `Pan.masked()` returns 8 chars regardless of PAN length — four asterisks
  prefix + last 4 digits — to prevent PAN-length leakage in logs.
- `TapType`, `TripStatus` are flat enums.
- `Tap` and `Trip` are records; `Trip` permits null `started`/`fromStop` for
  UNMATCHED_OFF audit rows.

## References

- Design doc §11 Value types and §5 Money and currency.
- PRD §Testing Decisions > Value-Type Scenarios.
