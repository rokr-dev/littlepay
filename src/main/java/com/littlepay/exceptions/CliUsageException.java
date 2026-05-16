package com.littlepay.exceptions;

/**
 * Thrown when the CLI arguments are invalid or missing. Exit code 2.
 */
public class CliUsageException extends LittlepayException {
    public CliUsageException(String message) {
        super(message, 2);
    }
}
