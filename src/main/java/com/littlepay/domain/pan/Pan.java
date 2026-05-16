package com.littlepay.domain.pan;

import java.util.Objects;

/**
 * Payment Account Number. {@link #masked()} always returns exactly 8 chars
 * (four asterisks + last 4 digits) regardless of PAN length, preventing
 * PAN-length leakage in logs.
 */
public record Pan(String value) {

    public Pan {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PAN must not be blank");
        }
    }

    /** Returns {@code "****XXXX"} — always 8 characters. */
    public String masked() {
        String last4 = value.substring(value.length() - 4);
        return "****" + last4;
    }
}
