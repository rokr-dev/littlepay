package com.littlepay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.littlepay.exceptions.CliUsageException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class CliTest {

    @Test
    void parsesNamedInputAndOutputFlags() {
        CliArgs args = Cli.parse(new String[]{"--input", "input.csv", "--output", "output.csv"});
        assertThat(args.inputPath()).isEqualTo("input.csv");
        assertThat(args.outputPath()).isEqualTo("output.csv");
        assertThat(args.faresPath()).isEqualTo("fares.csv");
        assertThat(args.duplicateWindowSeconds()).isEqualTo(30);
        assertThat(args.help()).isFalse();
    }

    @Test
    void parsesFaresFlagOverride() {
        CliArgs args = Cli.parse(
                new String[]{"--input", "input.csv", "--output", "output.csv", "--fares", "/custom/fares.csv"});
        assertThat(args.faresPath()).isEqualTo("/custom/fares.csv");
    }

    @Test
    void parsesDuplicateWindowSecondsFlag() {
        CliArgs args = Cli.parse(
                new String[]{"--input", "input.csv", "--output", "output.csv", "--duplicate-window-seconds", "60"});
        assertThat(args.duplicateWindowSeconds()).isEqualTo(60);
    }

    @Test
    void helpFlagPrintsUsageToStdoutAndExitsZero() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream savedOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            CliArgs args = Cli.parse(new String[]{"-h"});
            assertThat(args.help()).isTrue();
            assertThat(out.toString()).containsIgnoringCase("usage");
        } finally {
            System.setOut(savedOut);
        }
    }

    @Test
    void invalidDuplicateWindowThrowsUsageException() {
        assertThatThrownBy(() -> Cli.parse(
                        new String[]{"--input", "in.csv", "--output", "out.csv", "--duplicate-window-seconds", "abc"}))
                .isInstanceOf(CliUsageException.class)
                .hasMessageContaining("abc");
    }

    @Test
    void missingInputFlagThrowsUsageException() {
        assertThatThrownBy(() -> Cli.parse(new String[]{"--output", "out.csv"}))
                .isInstanceOf(CliUsageException.class)
                .hasMessageContaining("--input");
    }

    @Test
    void missingOutputFlagThrowsUsageException() {
        assertThatThrownBy(() -> Cli.parse(new String[]{"--input", "in.csv"}))
                .isInstanceOf(CliUsageException.class)
                .hasMessageContaining("--output");
    }

    @Test
    void noArgumentsPrintsUsageToStderrAndExitsTwo() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream savedErr = System.err;
        System.setErr(new PrintStream(err));
        try {
            assertThatThrownBy(() -> Cli.parse(new String[]{}))
                    .isInstanceOf(CliUsageException.class)
                    .hasMessageContaining("usage");
            assertThat(err.toString()).containsIgnoringCase("usage");
        } finally {
            System.setErr(savedErr);
        }
    }
}
