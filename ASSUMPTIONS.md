# Assumptions

This file lists every domain assumption made during implementation, with brief rationale.
Full reasoning, alternatives considered, and interview talking points are in the design decisions document.

---

## Matching key

**Assumption:** taps are matched on `(PAN, BusID)` per UTC calendar-day bucket.

`PAN` alone would incorrectly pair an OFF on bus A with a later ON on bus B. `BusID` implicitly scopes the operating company (a bus belongs to one company), making `CompanyId` redundant for matching purposes. The output trip still carries `CompanyId` from the ON tap.

UTC calendar day was chosen because the input column is named `DateTimeUTC` and it gives an unambiguous, locale-independent cutoff. A service-day boundary (e.g., rolling at 03:00) would be more realistic for transit operations but requires timezone configuration not present in the spec.

---

## Duplicate detection window

**Assumption:** a second consecutive ON tap from the same `(PAN, BusID)` within 30 seconds of the first is treated as a duplicate hardware misfire and dropped silently (WARN log only, no audit row).

A second ON arriving after the 30-second window is treated as a new trip leg — the first ON is closed as INCOMPLETE and charged the maximum fare from its origin stop.

The 30-second default is configurable via `--duplicate-window-seconds`. Setting it to `0` disables duplicate detection entirely. The window applies uniformly regardless of whether the two consecutive ONs are at the same stop or different stops.

---

## Calendar-day boundary

**Assumption:** a trip that straddles UTC midnight is split into two rows: the ON side becomes INCOMPLETE in the first day's bucket; the OFF side becomes UNMATCHED_OFF in the second day's bucket.

This is the acknowledged cost of the UTC calendar-day matching key. A service-day boundary would prevent the split but introduces timezone configuration. Noted as a future extension.

---

## Incomplete trip charge

**Assumption:** an INCOMPLETE trip (ON tap with no matching OFF before end-of-day) is charged the maximum fare available from the origin stop across all destinations in the fare table.

This is the most passenger-unfriendly policy that is still defensible: it creates a strong incentive to tap off, avoids revenue leakage, and is explicitly called out in the brief's example fare data. A zero charge would invite deliberate non-touch-off.

---

## UNMATCHED_OFF audit row

**Assumption:** an OFF tap with no prior ON tap in the same `(PAN, BusID, day)` bucket is emitted as an `UNMATCHED_OFF` row with zero charge rather than being silently dropped.

The brief defines three trip statuses (COMPLETED, INCOMPLETE, CANCELLED) but does not explicitly address orphaned OFFs. Dropping them silently is the simplest path but produces "where did my tap go?" support tickets in any real fare-reconciliation system. Emitting UNMATCHED_OFF gives operators a single source of truth for tap reconciliation against fare-gate data without weakening the three spec-defined statuses.

Fields for the absent ON tap (FromStopId, Started, DurationSecs) are left empty. CompanyId and BusID are sourced from the OFF tap.

---

## AUD hard-coded

**Assumption:** all monetary amounts are Australian Dollars (AUD).

The spec uses `$` notation and Littlepay is an Australian company. The `Money` value type accepts a `Currency` parameter internally, so adding multi-currency support later does not require a type redesign. Joda-Money or JSR-354 would be the upgrade path for any real multi-currency requirement.

---

## Fare matrix symmetry

**Assumption:** the fare matrix is unordered — a single row covers travel in both directions between a stop pair.

The fare matrix schema uses three columns (`FromStopId, ToStopId, Amount`). Rows are normalised to a canonical `StopPair` key (stops sorted lexicographically) at load time. A duplicate row for the reverse direction is rejected at load time with exit code 6. This means a COMPLETED trip from Stop A to Stop B and a COMPLETED trip from Stop B to Stop A are charged the same fare.

---

## PAN masking scope

**Assumption:** PAN masking (four-asterisk prefix, last four digits visible) applies to log output only. The output `trips.csv` contains raw PANs.

The output file is the data product — masking it would break downstream settlement and reconciliation. Log masking is hygiene against accidental exposure in log aggregation pipelines. Full PCI-DSS scope would bring additional storage, transmission, and retention controls that are outside this exercise.
