package com.littlepay;

/**
 * Thrown when a tap references a StopId not present in the fare table. Exit code 5.
 */
public class UnknownStopException extends LittlepayException {

    public UnknownStopException(String message) {
        super(message, 5);
    }
}
