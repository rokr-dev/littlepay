package com.littlepay.cli;

import com.littlepay.exceptions.CliUsageException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliTest {

    @Test
    void parses_two_positional_arguments() {
        CliArgs args = Cli.parse(new String[]{"input.csv", "output.csv"});
        assertThat(args.inputPath()).isEqualTo("input.csv");
        assertThat(args.outputPath()).isEqualTo("output.csv");
        assertThat(args.faresPath()).isEqualTo("fares.csv");
        assertThat(args.duplicateWindowSeconds()).isEqualTo(30);
        assertThat(args.help()).isFalse();
    }

    @Test
    void parses_fares_flag_override() {
        CliArgs args = Cli.parse(new String[]{"input.csv", "output.csv", "--fares", "/custom/fares.csv"});
        assertThat(args.faresPath()).isEqualTo("/custom/fares.csv");
    }

    @Test
    void parses_duplicate_window_seconds_flag() {
        CliArgs args = Cli.parse(new String[]{"input.csv", "output.csv", "--duplicate-window-seconds", "60"});
        assertThat(args.duplicateWindowSeconds()).isEqualTo(60);
    }

    @Test
    void help_flag_prints_usage_to_stdout_and_exits_zero() {
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
    void invalid_duplicate_window_throws_usage_exception() {
        assertThatThrownBy(() -> Cli.parse(new String[]{"in.csv", "out.csv", "--duplicate-window-seconds", "abc"}))
                .isInstanceOf(CliUsageException.class)
                .hasMessageContaining("abc");
    }

    @Test
    void no_arguments_prints_usage_to_stderr_and_exits_two() {
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
