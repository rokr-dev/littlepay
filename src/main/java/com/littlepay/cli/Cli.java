package com.littlepay.cli;

import com.littlepay.exceptions.CliUsageException;

/**
 * Hand-rolled CLI argument parser. No third-party CLI library.
 * <p>
 * Usage: --input &lt;path&gt; --output &lt;path&gt;
 *   [--fares &lt;path&gt;] [--duplicate-window-seconds &lt;N&gt;] [-h|--help]
 */
public class Cli {

    private static final String USAGE =
            "usage: --input <path> --output <path>"
            + " [--fares <path>] [--duplicate-window-seconds <N>] [-h|--help]";

    private Cli() {}

    /**
     * Parse {@code args} into a {@link CliArgs}.
     *
     * <ul>
     *   <li>No arguments → prints usage to stderr and throws {@link CliUsageException}.</li>
     *   <li>{@code -h} / {@code --help} → prints usage to stdout and returns a sentinel with {@code help=true}.</li>
     *   <li>Otherwise {@code --input} and {@code --output} are required named flags.</li>
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
            if ("--input".equals(arg)) {
                i++;
                inputPath = args[i];
            } else if ("--output".equals(arg)) {
                i++;
                outputPath = args[i];
            } else if ("--fares".equals(arg)) {
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
            }
            i++;
        }

        if (inputPath == null) {
            System.err.println(USAGE);
            throw new CliUsageException("usage: missing required flag --input — " + USAGE);
        }
        if (outputPath == null) {
            System.err.println(USAGE);
            throw new CliUsageException("usage: missing required flag --output — " + USAGE);
        }

        return new CliArgs(inputPath, outputPath, faresPath, duplicateWindowSeconds, false);
    }
}
