package com.littlepay.exceptions;

/**
 * Base exception for all fail-fast errors in the Littlepay tool.
 * Each subclass carries its documented exit code (§9.2 of design doc).
 */
public abstract class LittlepayException extends RuntimeException {

    private final int exitCode;

    protected LittlepayException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    protected LittlepayException(String message, int exitCode, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
