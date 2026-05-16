package com.littlepay.exceptions;

/**
 * Thrown when a tap CSV row is malformed, has a bad timestamp, unknown TapType,
 * or duplicate ID. Exit code 4.
 */
public class TapRowException extends LittlepayException {

    public TapRowException(String message) {
        super(message, 4);
    }

    public TapRowException(String message, Throwable cause) {
        super(message, 4, cause);
    }
}
