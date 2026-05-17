package com.littlepay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.littlepay.exceptions.CliUsageException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class CliTest {

    @Test
    void parsesTwoPositionalArguments() {
        CliArgs args = Cli.parse(new String[]{"input.csv", "output.csv"});
        assertThat(args.inputPath()).isEqualTo("input.csv");
        assertThat(args.outputPath()).isEqualTo("output.csv");
        assertThat(args.faresPath()).isEqualTo("fares.csv");
        assertThat(args.duplicateWindowSeconds()).isEqualTo(30);
        assertThat(args.help()).isFalse();
    }

    @Test
    void parsesFaresFlagOverride() {
        CliArgs args = Cli.parse(new String[]{"input.csv", "output.csv", "--fares", "/custom/fares.csv"});
        assertThat(args.faresPath()).isEqualTo("/custom/fares.csv");
    }

    @Test
    void parsesDuplicateWindowSecondsFlag() {
        CliArgs args = Cli.parse(new String[]{"input.csv", "output.csv", "--duplicate-window-seconds", "60"});
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
        assertThatThrownBy(() -> Cli.parse(new String[]{"in.csv", "out.csv", "--duplicate-window-seconds", "abc"}))
                .isInstanceOf(CliUsageException.class)
                .hasMessageContaining("abc");
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
