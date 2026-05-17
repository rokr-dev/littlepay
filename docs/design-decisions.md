# Design Decisions

Companion to [`littlepay-trip-PRD.md`](./littlepay-trip-PRD.md). The PRD describes *what* and *how*. This document records *why* — the tradeoffs and design choices an evaluator should see.

---

## 1. Architecture — Hexagonal-lite

The domain is pure at the centre with no I/O imports. Ports (interfaces) sit at the boundary, and CSV adapters are the only I/O-touching code. Dependencies point inward only.

What is kept: a pure domain package, boundary ports (`TapReader`, `TripWriter`, `FareTableLoader`), and CSV adapters that own all messy I/O concerns.

What is dropped: use-case classes per operation, an application service layer, formal DTO/domain mappers, a DI framework, and a domain-event bus. None of these are justified by a single-command CLI batch tool with one orchestration flow.

The matching state machine and pricing logic are unit-testable with zero filesystem dependencies or fixtures. The same domain would survive intact if the input were Kafka events — only a new adapter would be needed.

Alternatives rejected:
- A single `Main` with everything inline: this makes matcher logic untestable.
- Full hexagonal with use-case classes per operation: overkill for one command.
- Layered (controller/service/repository): web and persistence vocabulary does not fit a CLI batch tool.

**Dependency injection**: the wiring is hand-rolled constructor injection in `Main`. The full wiring graph fits in approximately 10 lines. Reaching for Spring, Guice, or Dagger at this project size is an anti-signal.

---

## 2. Domain Rules

### 2.1 Matching key — `(PAN, companyId, BusID, UTC-day)`

- PAN alone would incorrectly pair an OFF on Bus A with a prior ON on Bus B.
- BusID alone scopes the operating company in the common case, but `companyId` was added explicitly (see §2.2) to defend against BusID reuse across companies.
- The UTC calendar day aligns with the `DateTimeUTC` column name and provides an unambiguous, locale-independent cutoff.

Alternatives rejected:
- `(PAN)` alone: breaks the multi-bus case.
- `(PAN, BusID, local-day)`: requires a timezone that is not present in the data.
- Service-day boundary (e.g., 03:00 to 03:00): more realistic for transit operations, but the spec gives no signal of this. Noted as a future extension.

### 2.2 CompanyId in the bucket key

The original design used `(PAN, BusID, UTC-day)` on the grounds that a bus belongs to one company. `CompanyId` was added post-implementation after identifying the edge case where two companies share the same BusID string (for example, each operator has their own "Bus1"). Without `CompanyId` in the key, a tap from Company A's Bus1 could collide with Company B's Bus1 for the same PAN on the same day. Adding `CompanyId` is a defensive, additive change with no impact on the domain model or pricing logic.

### 2.3 Input ordering

Input is treated as unordered. Taps are grouped by `(PAN, companyId, BusID)`, then sorted within each group by `DateTimeUTC` ascending with the input `ID` ascending as the secondary key for stability.

The secondary sort on `ID` matters when two taps share an identical timestamp. Without it, sort stability is JVM-implementation dependent and the same input can produce different output — for example, a batched ON and OFF written in the same second could match as either CANCELLED or as UNMATCHED_OFF + INCOMPLETE.

### 2.4 Trip statuses

**COMPLETED** — An ON followed by an OFF at a different stop. Charged the fare-matrix amount for that stop pair.

**INCOMPLETE** — An ON with no matching OFF before the end of the UTC day. Charged the maximum fare available from the origin stop. This matches standard transit industry practice: Opal (NSW), Myki (Victoria), TfL Oyster (London), Singapore EZ-Link, and Hong Kong Octopus all charge the maximum incomplete journey fare on a missed touch-off. The policy (a) protects operator revenue against systematic non-tap-off and (b) creates a pricing incentive for passengers to tap off.

**CANCELLED** — An ON followed by an OFF at the same stop. Zero charge.

**UNMATCHED_OFF** — An OFF tap with no prior ON in the bucket. Zero charge. Emitted as an audit row with null ON-side fields. The alternative — silently dropping orphan OFFs — produces reconciliation gaps ("where did my tap go?" support tickets). Emitting UNMATCHED_OFF gives operators a single source of truth for tap reconciliation against fare-gate data.

### 2.5 Consecutive ONs rule

The spec is silent on consecutive ONs. Eight readings were considered; the final rule (Read H) is:

A configurable duplicate-detection window (default 30 seconds, `--duplicate-window-seconds`) is applied symmetrically to consecutive ONs regardless of whether the stops are the same:

- **Within the window**: the second ON is dropped as a duplicate or hardware misfire. The first ON remains active. A WARN log records the drop.
- **Outside the window**: the first ON closes as INCOMPLETE (max-from-origin fare); the second ON begins a new active leg.

The window is applied symmetrically to both same-stop and cross-stop cases. This catches same-stop hardware misfires and cross-stop data corruption with one rule, rather than case-splitting on stop identity.

**Rejected readings:**
- Read A (first ON always INCOMPLETE): charges $12.80 for a Stop1→Stop3 journey the matrix prices directly at $7.30 — customer-hostile even when the second ON is a data-corruption artefact within milliseconds.
- Read B (first ON always CANCELLED, $0): extends CANCELLED beyond its ON+OFF same-stop definition.
- Read C (introduce a fifth status): adds a status the spec never anticipated.
- Read D (case-split same-stop vs different-stop, no time window): cannot distinguish a same-stop double-tap at boarding from a same-stop tap hours later.
- Read E (collapse all consecutive ONs to one trip): breaks the round-trip case — ON@1, ON@3, OFF@1 would incorrectly become a CANCELLED $0 trip.
- Read F (same-stop dedup, different-stop split): still cannot tell a same-stop misfire from a same-stop "I came back later."
- Read G (Read F + 30s window for same-stop only): leaves a gap for cross-stop data corruption (ON@1, ON@2 within 5 seconds — impossibly fast bus movement).

**Default window justification**: 30 seconds is defensible because hardware misfires happen sub-second, accidental double-taps happen in 1–3 seconds, and bus dwell time at a stop is typically 30–60 seconds — making two taps within 30 seconds indicative of a single boarding event. The value is exposed as a CLI flag so operators can tune it against real production data.

**Known limitation**: ON@1, ON@2 minutes apart followed by OFF@3 charges $12.80 (INCOMPLETE max-from-Stop1 + COMPLETED Stop2→Stop3), while the matrix has a direct Stop1→Stop3 fare of $7.30. This is deliberate industry-aligned behaviour — all comparable real-world systems price per leg. A `--fare-mode favour-passenger` option to cap at the direct fare is feasible as a 30-line addition but is deliberately out of v1 scope: operator commercial policy is not specified, and per-leg default pricing is what every comparable real-world system does.

**Worked examples:**

| Input sequence | Result | Total |
|---|---|---|
| ON@1 09:00, ON@1 09:00:05, OFF@2 | gap ≤ 30s → dedup → COMPLETED 1→2 | $3.25 |
| ON@1 09:00, ON@1 09:00:35, OFF@2 | gap > 30s → split → INCOMPLETE@1 + COMPLETED 1→2 | $10.55 |
| ON@1 09:00, ON@2 09:00:05, OFF@3 | gap ≤ 30s → collapse → COMPLETED 1→3 | $7.30 |
| ON@1 09:00, ON@2 09:05, OFF@3 | gap > 30s → split → INCOMPLETE@1 + COMPLETED 2→3 | $12.80 |
| ON@1, ON@3, OFF@1 (minutes apart) | split → INCOMPLETE@1 + COMPLETED 3→1 | $14.60 |
| ON@1, ON@2, ON@3, OFF@1 | all split → INCOMPLETE@1 + INCOMPLETE@2 + COMPLETED 3→1 | $20.10 |

### 2.6 UTC day boundary

A passenger who taps ON at 23:55 UTC and OFF at 00:05 UTC the next day produces:
- ON@23:55 → INCOMPLETE in the first day's bucket (no OFF arrived before end-of-UTC-day).
- OFF@00:05 → UNMATCHED_OFF in the second day's bucket (no ON exists in that bucket).

This is the cost of choosing the UTC calendar day as the boundary. A service-day boundary would smooth this case but introduces additional configuration that is not signalled by the spec.

---

## 3. Money and Currency

### 3.1 Money type — `BigDecimal` wrapped in a value record

Binary floating point cannot represent $0.10 exactly. Using `double` for currency is an industry-known footgun and an instant red flag in a finance-adjacent submission.

The `Money` record wraps `BigDecimal` (scale 2, rounding HALF_UP) and `Currency`. Wrapping in a value type:
- Centralises the `$X.XX` formatting rule (single source of truth).
- Prevents accidental currency mixing if multi-currency support arrives later.
- Records eliminate boilerplate; `equals`, `hashCode`, and `toString` come free with the canonical compact constructor enforcing scale.

Alternatives rejected:
- `double` / `float`: binary floating-point precision problem.
- Long cents (`325L`): compact but loses self-documenting types and makes it easy to mix cents and dollars.
- Raw `BigDecimal` everywhere: scatters formatting and lacks currency awareness.
- Joda-Money / JSR-354: the correct production choice for multi-currency, but adds a dependency for one currency in a bounded exercise.

### 3.2 AUD hard-coded

The spec uses `$` notation and Littlepay is an Australian company. The `Money` type accepts `Currency`, so multi-currency is a future upgrade without a type redesign. Joda-Money / JSR-354 is the documented upgrade path.

### 3.3 Locale pinning

All `DateTimeFormatter` and `BigDecimal` formatting calls are explicitly pinned to `Locale.ROOT` to prevent output drift on reviewer machines with different default locales.

---

## 4. Fare Matrix

### 4.1 Schema

Three-column CSV: `FromStopId,ToStopId,Amount`. Two separate columns for the stop pair (rather than a single hyphenated column) are used because stop names can contain hyphens — a separator-based scheme would be ambiguous. One row is stored per unordered pair; symmetry is encoded in the normalised lookup key.

The `Amount` column must match `^\d+\.\d{2}$` exactly. No currency symbol, no thousand separators, no scientific notation, and exactly two decimal places are permitted. Silent rounding of fare data is a correctness bug; the loader must reject anything ambiguous rather than coerce it.

### 4.2 Normalised StopPair

```java
public record StopPair(StopId a, StopId b) {
    public StopPair {
        if (a.value().compareTo(b.value()) > 0) {
            var t = a; a = b; b = t;
        }
    }
}
```

This guarantees that Stop1↔Stop2 and Stop2↔Stop1 resolve to the same key. Fare-table symmetry is encoded in the key rather than by storing duplicate rows.

### 4.3 Load-time validation

The `FareTableLoader` validates the full fare table at startup before any pricing occurs: the header must match exactly; amounts must be positive and non-zero; duplicate pairs in either direction fail fast. Once the tap file is read, any stop referenced in a tap that has no fare entry triggers a fail-fast with exit code 5 — a silent fallback would understate revenue.

### 4.4 `maxFareFrom(stop)`

This method is used to price INCOMPLETE trips. It is precomputed at load time per stop (N is small; pricing happens per trip). It returns the highest fare reachable from a given stop across the entire fare matrix.

---

## 5. I/O

### 5.1 Input robustness

- The UTF-8 BOM (`EF BB BF`) is stripped before header validation. Excel and other Windows-origin exports routinely include a BOM; failing on it would produce a confusing header-mismatch error.
- Every field is `.strip()`-ed before validation to tolerate trailing whitespace from spreadsheet exporters.
- Blank lines are skipped with a DEBUG log rather than treated as malformed.
- The header is validated strictly after BOM and whitespace normalisation.
- TapType parsing is case-insensitive.
- Duplicate `ID` values fail fast with exit code 4.

### 5.2 Output formatting

- `ChargeAmount` is rendered as `$X.XX` always, including `$0.00` for CANCELLED and UNMATCHED_OFF, so the reviewer can confirm that zero-charge rows were processed rather than blank-fielded.
- PAN is raw in the output (it is the data product); masking applies to logs only.
- A space is included after each comma to match the brief sample cosmetically.
- The `CSVFormat` record separator is set to `\n` explicitly — the Commons CSV default is CRLF on macOS, which breaks golden-file diffs on cross-platform comparison.

### 5.3 Output ordering

Primary key: `Started` ascending. UNMATCHED_OFF rows (which have no `Started` value) are sorted by `Finished` ascending. The tiebreaker is input `ID` ascending. Deterministic ordering makes the golden-file E2E test stable.

---

## 6. Error Handling

### 6.1 Fail-fast policy

Validate at boundaries; trust inside. `CsvTapReader` handles all parsing, enum mapping, and timestamp validation and returns `Tap` records. Inside the domain, types are trusted — no defensive checks are performed inside the matcher or pricer.

Distinct exit codes are used per failure class so a surrounding pipeline (cron, Airflow) can branch on cause without parsing log output:

| Failure | Exit code |
|---|---|
| Input or fare file missing / unreadable | 2 |
| Tap CSV header mismatch | 3 |
| Tap CSV malformed row, bad timestamp, unknown TapType, duplicate ID | 4 |
| Unknown StopId in tap data (not in fare table) | 5 |
| Fare table validation failure | 6 |
| Uncaught RuntimeException | 1 |

### 6.2 Exception hierarchy

```
LittlepayException (RuntimeException)
├── ConfigException                  fare-file issues
├── InputException                   tap-file issues
│   ├── HeaderMismatchException
│   ├── MalformedRowException        carries rowNumber, raw text, cause
│   └── DuplicateIdException
└── PricingException
    └── UnknownStopException         carries stopId
```

`Main` catches `LittlepayException`, prints a clear message to `System.err`, and sets the exit code per type. An uncaught `RuntimeException` produces exit code 1.

---

## 7. Value Types

### 7.1 StopId and Pan as distinct types

Wrapping `String` in distinct record types (`StopId`, `Pan`) prevents accidental interchange — a stop cannot be passed where a PAN is expected and vice versa. The compiler enforces what would otherwise be a runtime assertion.

### 7.2 PAN masking

```java
public String masked() {
    int n = value.length();
    if (n < 4) return "****";
    return "****" + value.substring(n - 4);
}
```

The constant-width four-asterisk prefix is intentional. A variable-length mask (one star per masked digit) would leak the PAN length, which hints at the issuer family (Amex 15, Visa/Mastercard 16, some Maestro/Diners 19). The output always begins with exactly four asterisks regardless of input length. This is log hygiene, not full PCI-DSS compliance — real PCI scope brings additional storage, transmission, and retention controls outside this exercise.

---

## 8. Testing

### 8.1 Test pyramid

```
        ┌──────────────┐
        │  Golden E2E  │   1 test: spec sample input → expected trips.csv
        └──────────────┘
      ┌──────────────────┐
      │  Integration     │   ~6: CSV reader/writer roundtrip + error paths
      └──────────────────┘
    ┌──────────────────────┐
    │  Domain logic        │   ~25 matcher + ~15 pricer
    └──────────────────────┘
  ┌──────────────────────────┐
  │  Value-type contracts    │   ~15: Money, StopPair, Pan, Tap invariants
  └──────────────────────────┘
```

### 8.2 Framework choices

JUnit 5 with AssertJ. `@Nested` is used for grouping scenarios by behaviour. `@DisplayName` carries sentence-form names. `@ParameterizedTest` + `@CsvSource` handle fare-matrix symmetry and duplicate-window boundary thresholds. No mocking framework is used — the domain core is pure (no I/O), and hand-rolled fakes at the ports are simpler and more readable than Mockito stubs for this codebase size.

No code-coverage gate: coverage measurement is fine; gating builds on a magic number is gaming.

No property-based testing (jqwik) or mutation testing (Pitest): both would add dependencies and complexity beyond what the exercise demands.

All fixtures use fixed `Instant` values — no `Thread.sleep`, no real-clock dependencies. No shared mutable state exists between tests.

### 8.3 Golden E2E test

The golden file (`trips-sample-expected.csv`) is hand-derived from the spec and committed to the repo. It doubles as the "output file for the example input" deliverable. The test runs `App` end-to-end, writes output to a temp file, and compares it line-by-line. The file is duplicated under `examples/` for reviewer convenience — two physical files rather than a symlink, to avoid symlink handling inconsistencies on Windows.

---

## 9. Deferred Scope Items

These are deliberate v1 exclusions, not oversights:

- **`--fare-mode favour-passenger`**: an opt-in operator policy to cap multi-leg charges at the direct end-to-end fare when one exists. Per-leg pricing is the industry default (Opal, Myki, TfL Oyster, EZ-Link, Octopus). Operator commercial policy is not specified; a silent cap would also absorb the missed-OFF signal that operators need for reader calibration and driver behaviour monitoring.
- **Service-day boundary**: a configurable cutoff (e.g., 03:00 to 03:00) aligned with transit operations. The spec uses UTC timestamps and gives no service-day signal. Noted as a possible future extension.
- **`--daily-cap-per-pan`**: additive via a `DailyCapPolicy` port and a post-pricing aggregation pass over `(PAN, UTC-day)`. The insertion point sits cleanly between matcher output and `TripWriter` input. Out of scope because operator fare-ceiling policy is not specified.
- **Streaming I/O for multi-GB inputs**: the architecture supports a streaming adapter replacing the in-memory `CsvTapReader`; out of scope per the bounded-input assumption in the brief.
- **Multi-currency**: the `Money` type accepts `Currency`; Joda-Money / JSR-354 is the upgrade path once multi-currency is required.
