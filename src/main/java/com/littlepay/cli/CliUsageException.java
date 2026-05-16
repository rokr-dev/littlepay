package com.littlepay.cli;

/**
 * @deprecated Moved to {@link com.littlepay.exceptions.CliUsageException}.
 * This alias exists only for source-level backward compatibility during migration.
 */
@Deprecated
public class CliUsageException extends com.littlepay.exceptions.CliUsageException {
    public CliUsageException(String message) {
        super(message);
    }
}
