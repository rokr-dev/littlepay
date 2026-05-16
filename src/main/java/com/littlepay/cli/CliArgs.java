package com.littlepay.cli;

/**
 * Parsed command-line arguments.
 */
public record CliArgs(
        String inputPath,
        String outputPath,
        String faresPath,
        int duplicateWindowSeconds,
        boolean help
) {
    public static final String DEFAULT_FARES_PATH = "fares.csv";
    static final int DEFAULT_DUPLICATE_WINDOW_SECONDS = 30;
}
