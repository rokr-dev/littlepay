package com.littlepay;

/**
 * Thrown when the tap CSV header does not match the expected schema. Exit code 3.
 */
public class TapHeaderException extends LittlepayException {

    public TapHeaderException(String message) {
        super(message, 3);
    }
}
