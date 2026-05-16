package com.littlepay;

/**
 * Thrown when fare table CSV fails validation (bad header, invalid amount,
 * duplicate pair, etc.). Exit code 6.
 */
public class FareTableException extends LittlepayException {

    public FareTableException(String message) {
        super(message, 6);
    }

    public FareTableException(String message, Throwable cause) {
        super(message, 6, cause);
    }
}
