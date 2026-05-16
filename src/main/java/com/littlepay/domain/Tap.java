package com.littlepay.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A single tap event at a transit reader.
 *
 * @param id        unique tap identifier
 * @param dateTime  timestamp of the tap
 * @param tapType   ON or OFF
 * @param stopId    stop where the tap occurred
 * @param companyId operator company
 * @param busId     vehicle identifier
 * @param pan       payment account number
 */
public record Tap(
        long id,
        LocalDateTime dateTime,
        TapType tapType,
        StopId stopId,
        String companyId,
        String busId,
        Pan pan
) {
    public Tap {
        Objects.requireNonNull(dateTime, "dateTime");
        Objects.requireNonNull(tapType, "tapType");
        Objects.requireNonNull(stopId, "stopId");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(busId, "busId");
        Objects.requireNonNull(pan, "pan");
    }
}
