package com.littlepay.exceptions;

/**
 * Thrown when an input or fare file is missing or unreadable. Exit code 2.
 */
public class InputFileException extends LittlepayException {

    public InputFileException(String message) {
        super(message, 2);
    }

    public InputFileException(String message, Throwable cause) {
        super(message, 2, cause);
    }
}
