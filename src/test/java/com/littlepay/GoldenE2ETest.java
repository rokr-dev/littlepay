package com.littlepay;

import com.littlepay.io.CsvTapReader;
import com.littlepay.io.CsvTripWriter;
import com.littlepay.io.FareTableLoader;
import com.littlepay.matching.StateMachineTripMatcher;
import com.littlepay.pricing.FareTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden end-to-end test: runs App against the verbatim spec sample input and
 * compares output line-by-line to the hand-derived expected file.
 *
 * <p>Also verifies that {@code examples/} copies are byte-identical to the
 * {@code src/test/resources/} originals.
 */
class GoldenE2ETest {

    @TempDir
    Path tempDir;

    // ── helpers ──────────────────────────────────────────────────────────────

    private Path resource(String name) throws URISyntaxException {
        var url = GoldenE2ETest.class.getResource("/" + name);
        assertThat(url).as("test resource not found: " + name).isNotNull();
        return Paths.get(url.toURI());
    }

    private App buildApp(Path faresPath, Path tapsPath, Path outputPath) {
        FareTable fareTable = new FareTableLoader().load(faresPath);
        StateMachineTripMatcher matcher = new StateMachineTripMatcher(fareTable, 30L);
        return new App(new CsvTapReader(), matcher, new CsvTripWriter(),
                fareTable, tapsPath, outputPath);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void sample_input_produces_expected_output_line_by_line() throws IOException, URISyntaxException {
        // arrange
        Path taps     = resource("taps-sample.csv");
        Path expected = resource("trips-sample-expected.csv");
        Path fares    = resource("fares-sample.csv");
        Path actual   = tempDir.resolve("trips-actual.csv");

        // act
        buildApp(fares, taps, actual).run();

        // assert — line-by-line equality, zero diffs
        List<String> actualLines   = Files.readAllLines(actual);
        List<String> expectedLines = Files.readAllLines(expected);

        assertThat(actualLines)
                .as("output line count must match expected")
                .hasSameSizeAs(expectedLines);

        for (int i = 0; i < expectedLines.size(); i++) {
            assertThat(actualLines.get(i))
                    .as("line %d mismatch", i + 1)
                    .isEqualTo(expectedLines.get(i));
        }
    }

    @Test
    void examples_copies_match_test_resources() throws IOException, URISyntaxException {
        // arrange — Gradle sets CWD to projectDir during test execution
        Path projectRoot = Path.of("").toAbsolutePath();

        Path examplesTaps     = projectRoot.resolve("examples/taps-sample.csv");
        Path examplesExpected = projectRoot.resolve("examples/trips-sample-expected.csv");

        Path testTaps         = resource("taps-sample.csv");
        Path testExpected     = resource("trips-sample-expected.csv");

        // assert — byte-identical
        assertThat(examplesTaps)
                .as("examples/taps-sample.csv must exist")
                .exists();
        assertThat(examplesExpected)
                .as("examples/trips-sample-expected.csv must exist")
                .exists();

        assertThat(Files.readAllBytes(examplesTaps))
                .as("examples/taps-sample.csv must be byte-identical to src/test/resources/taps-sample.csv")
                .isEqualTo(Files.readAllBytes(testTaps));

        assertThat(Files.readAllBytes(examplesExpected))
                .as("examples/trips-sample-expected.csv must be byte-identical to src/test/resources/trips-sample-expected.csv")
                .isEqualTo(Files.readAllBytes(testExpected));
    }
}
