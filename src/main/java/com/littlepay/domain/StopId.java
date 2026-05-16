package com.littlepay.domain;

import java.util.Objects;

/**
 * Identifies a transit stop. Rejects null or blank values.
 */
public record StopId(String value) {

    public StopId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("StopId must not be blank");
        }
    }
}
