package com.littlepay.domain;

import com.littlepay.domain.pan.Pan;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A completed, incomplete, or cancelled trip derived from tap pairs.
 *
 * <p>{@code started} and {@code fromStop} are nullable for UNMATCHED_OFF audit rows
 * where no matching tap-on exists.
 *
 * @param started       tap-on timestamp, or null for UNMATCHED_OFF
 * @param finished      tap-off timestamp
 * @param durationSecs  trip duration in seconds (0 if started is null)
 * @param fromStop      tap-on stop, or null for UNMATCHED_OFF
 * @param toStop        tap-off stop
 * @param chargeAmount  fare charged
 * @param companyId     operator company
 * @param busId         vehicle identifier
 * @param pan           payment account number
 * @param status        COMPLETED, INCOMPLETE, or CANCELLED
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
        Objects.requireNonNull(finished, "finished");
        Objects.requireNonNull(toStop, "toStop");
        Objects.requireNonNull(chargeAmount, "chargeAmount");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(busId, "busId");
        Objects.requireNonNull(pan, "pan");
        Objects.requireNonNull(status, "status");
        // started and fromStop may be null for UNMATCHED_OFF rows
    }
}
