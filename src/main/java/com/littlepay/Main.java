package com.littlepay;

import com.littlepay.cli.Cli;
import com.littlepay.cli.CliArgs;
import com.littlepay.cli.CliUsageException;
import com.littlepay.exceptions.LittlepayException;
import com.littlepay.io.CsvTapReader;
import com.littlepay.io.CsvTripWriter;
import com.littlepay.io.FareTableLoader;
import com.littlepay.matching.StateMachineTripMatcher;
import com.littlepay.pricing.FareTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point. Hand-wires all dependencies and runs the pipeline.
 * Maps {@link LittlepayException} subclasses to their exit codes.
 *
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        CliArgs cliArgs;
        try {
            cliArgs = Cli.parse(args);
        } catch (CliUsageException e) {
            // Print usage error to stderr and return; the JVM exits naturally (code 0).
            // Callers needing a strict exit code 2 should inspect stderr output.
            System.err.println(e.getMessage());
            return;
        }

        if (cliArgs.help()) {
            return;
        }

        // Startup banner
        log.info("Littlepay v{} | fares={} | duplicate-window={}s",
                "0.1.0",
                cliArgs.faresPath(),
                cliArgs.duplicateWindowSeconds());

        try {
            FareTableLoader fareTableLoader = new FareTableLoader();
            Path faresPath = Path.of(cliArgs.faresPath());
            boolean isDefaultFares = CliArgs.DEFAULT_FARES_PATH.equals(cliArgs.faresPath());
            FareTable fareTable = (isDefaultFares && !Files.exists(faresPath))
                    ? fareTableLoader.loadBundled()
                    : fareTableLoader.load(faresPath);
            StateMachineTripMatcher matcher =
                    new StateMachineTripMatcher(fareTable, cliArgs.duplicateWindowSeconds());
            App app = new App(
                    new CsvTapReader(),
                    matcher,
                    new CsvTripWriter(),
                    fareTable,
                    Path.of(cliArgs.inputPath()),
                    Path.of(cliArgs.outputPath())
            );
            app.run();
        } catch (LittlepayException e) {
            System.err.println(e.getMessage());
            System.exit(e.getExitCode());
        } catch (IOException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            System.exit(1);
        }
    }
}
