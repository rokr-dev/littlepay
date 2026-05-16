package com.littlepay.cli;

import com.littlepay.exceptions.CliUsageException;

/**
 * Hand-rolled CLI argument parser. No third-party CLI library.
 *
 * Usage: littlepay <input> <output> [--fares <path>] [--duplicate-window-seconds <N>] [-h|--help]
 */
public class Cli {

    private static final String USAGE =
            "usage: littlepay <input> <output> [--fares <path>] [--duplicate-window-seconds <N>] [-h|--help]";

    private Cli() {}

    /**
     * Parse {@code args} into a {@link CliArgs}.
     *
     * <ul>
     *   <li>No arguments → prints usage to stderr and throws {@link CliUsageException}.</li>
     *   <li>{@code -h} / {@code --help} → prints usage to stdout and returns a sentinel with {@code help=true}.</li>
     *   <li>Otherwise expects exactly two positional args (input, output) plus optional flags.</li>
     * </ul>
     */
    public static CliArgs parse(String[] args) {
        if (args.length == 0) {
            System.err.println(USAGE);
            throw new CliUsageException("usage: no arguments provided — " + USAGE);
        }

        // Check for help flag before anything else
        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                System.out.println(USAGE);
                return new CliArgs(null, null,
                        CliArgs.DEFAULT_FARES_PATH,
                        CliArgs.DEFAULT_DUPLICATE_WINDOW_SECONDS,
                        true);
            }
        }

        String inputPath = null;
        String outputPath = null;
        String faresPath = CliArgs.DEFAULT_FARES_PATH;
        int duplicateWindowSeconds = CliArgs.DEFAULT_DUPLICATE_WINDOW_SECONDS;

        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if ("--fares".equals(arg)) {
                i++;
                faresPath = args[i];
            } else if ("--duplicate-window-seconds".equals(arg)) {
                i++;
                String windowVal = args[i];
                try {
                    duplicateWindowSeconds = Integer.parseInt(windowVal);
                } catch (NumberFormatException e) {
                    System.err.println(USAGE);
                    throw new CliUsageException(
                            "--duplicate-window-seconds requires an integer, got: '" + windowVal + "'");
                }
            } else if (arg.startsWith("-")) {
                // unknown flag — ignore for now
            } else if (inputPath == null) {
                inputPath = arg;
            } else if (outputPath == null) {
                outputPath = arg;
            }
            i++;
        }

        if (inputPath == null || outputPath == null) {
            System.err.println(USAGE);
            throw new CliUsageException("usage: missing positional arguments — " + USAGE);
        }

        return new CliArgs(inputPath, outputPath, faresPath, duplicateWindowSeconds, false);
    }
}
