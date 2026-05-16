package com.littlepay.cli;

/**
 * Thrown when the CLI arguments are invalid or missing. Caller should print usage to stderr and exit 2.
 */
public class CliUsageException extends RuntimeException {
    public CliUsageException(String message) {
        super(message);
    }
}
