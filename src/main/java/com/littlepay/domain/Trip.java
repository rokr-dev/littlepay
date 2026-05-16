package com.littlepay.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A completed, incomplete, cancelled, or unmatched-off trip derived from tap pairs.
 *
 * <p>Nullable fields by status:
 * <ul>
 *   <li>UNMATCHED_OFF: {@code started} and {@code fromStop} are null (no tap-on)</li>
 *   <li>INCOMPLETE: {@code finished} and {@code toStop} are null (no tap-off)</li>
 * </ul>
 *
 * @param started       tap-on timestamp, or null for UNMATCHED_OFF
 * @param finished      tap-off timestamp, or null for INCOMPLETE
 * @param durationSecs  trip duration in seconds (0 if no OFF tap)
 * @param fromStop      tap-on stop, or null for UNMATCHED_OFF
 * @param toStop        tap-off stop, or null for INCOMPLETE
 * @param chargeAmount  fare charged
 * @param companyId     operator company
 * @param busId         vehicle identifier
 * @param pan           payment account number
 * @param status        COMPLETED, INCOMPLETE, CANCELLED, or UNMATCHED_OFF
 */
public record Trip(
        LocalDateTime started,
        LocalDateTime finished,
        long durationSecs,
        StopId fromStop,
        StopId toStop,
        Money chargeAmount,
        String companyId,
        String busId,
        Pan pan,
        TripStatus status
) {
    public Trip {
        Objects.requireNonNull(chargeAmount, "chargeAmount");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(busId, "busId");
        Objects.requireNonNull(pan, "pan");
        Objects.requireNonNull(status, "status");
        // started/fromStop may be null for UNMATCHED_OFF rows
        // finished/toStop may be null for INCOMPLETE rows
    }
}
