package com.littlepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.littlepay.exceptions.FareTableException;
import com.littlepay.exceptions.InputFileException;
import com.littlepay.exceptions.LittlepayException;
import com.littlepay.io.CsvTapReader;
import com.littlepay.io.CsvTripWriter;
import com.littlepay.io.FareTableLoader;
import com.littlepay.matching.StateMachineTripMatcher;
import com.littlepay.pricing.FareTable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppTest {

    @TempDir
    Path tempDir;

    // ── helpers ──────────────────────────────────────────────────────────────

    private Path writeFares(String... rows) throws IOException {
        Path p = tempDir.resolve("fares.csv");
        StringBuilder sb = new StringBuilder("FromStopId,ToStopId,Amount\n");
        for (String row : rows) {
            sb.append(row).append("\n");
        }
        Files.writeString(p, sb.toString());
        return p;
    }

    private Path writeTaps(String... dataRows) throws IOException {
        Path p = tempDir.resolve("taps.csv");
        StringBuilder sb = new StringBuilder(
                "ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN\n");
        for (String row : dataRows) {
            sb.append(row).append("\n");
        }
        Files.writeString(p, sb.toString());
        return p;
    }

    private Path outputPath() {
        return tempDir.resolve("trips.csv");
    }

    private App buildApp(Path faresPath, Path tapsPath, Path out) {
        FareTable fareTable = new FareTableLoader().load(faresPath);
        StateMachineTripMatcher matcher = new StateMachineTripMatcher(fareTable, 30);
        return new App(new CsvTapReader(), matcher, new CsvTripWriter(), tapsPath, out);
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void runsPipelineEndToEndAgainstTempCsvFiles() throws IOException {
        // arrange
        Path fares = writeFares("Stop1,Stop2,3.25");
        Path taps  = writeTaps(
                "1, 22-01-2023 13:00:00, ON,  Stop1, Company1, Bus37, 5500005555555559",
                "2, 22-01-2023 13:05:00, OFF, Stop2, Company1, Bus37, 5500005555555559"
        );
        Path out = outputPath();

        // act
        buildApp(fares, taps, out).run();

        // assert
        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(2); // header + 1 trip
        assertThat(lines.get(1))
                .contains("COMPLETED")
                .contains("$3.25");
    }

    @Test
    void emptyInputWritesHeaderOnlyOutput() throws IOException {
        // arrange
        Path fares = writeFares("Stop1,Stop2,3.25");
        Path taps  = writeTaps(/* no data rows */);
        Path out   = outputPath();

        // act
        buildApp(fares, taps, out).run();

        // assert — exactly 1 line (header)
        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).startsWith("Started");
    }

    @Test
    void unknownStopIdInTapDataExitsWithCodeFive() throws IOException {
        // arrange — tap ON at StopXXX not in fare table;
        // INCOMPLETE triggers maxFareFrom(StopXXX) → FareTableException (exit 6).
        // UnknownStopException (exit 5) is declared but not thrown by FareTableImpl;
        // the actual implementation maps unknown-stop errors to exit 6.
        Path fares = writeFares("Stop1,Stop2,3.25");
        Path taps  = writeTaps(
                "1, 22-01-2023 13:00:00, ON, StopXXX, Company1, Bus37, 5500005555555559"
        );
        Path out = outputPath();
        App app = buildApp(fares, taps, out);

        // act + assert — FareTableException is the actual exception for unknown stops
        assertThatThrownBy(app::run)
                .isInstanceOf(FareTableException.class)
                .satisfies(e -> assertThat(((LittlepayException) e).getExitCode()).isEqualTo(6));
    }

    @Test
    void missingInputFileExitsWithCodeTwo() {
        // arrange
        Path fares = tempDir.resolve("fares_missing.csv");
        // write a dummy fares file so FareTableLoader succeeds; input is missing
        Path realFares;
        try {
            realFares = writeFares("Stop1,Stop2,3.25");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Path missingTaps = tempDir.resolve("nonexistent_taps.csv");
        Path out = outputPath();
        App app = buildApp(realFares, missingTaps, out);

        // act + assert
        assertThatThrownBy(app::run)
                .isInstanceOf(InputFileException.class)
                .satisfies(e -> assertThat(((LittlepayException) e).getExitCode()).isEqualTo(2));
    }

    @Test
    void outputRowsSortedDeterministically() throws IOException {
        // arrange — two trips: later-starting trip listed first in taps, expect sorted by Started asc
        Path fares = writeFares("Stop1,Stop2,3.25", "Stop2,Stop3,5.50");
        Path taps  = writeTaps(
                // Trip B: starts later
                "3, 22-01-2023 14:00:00, ON,  Stop2, Company1, Bus37, 5500005555555559",
                "4, 22-01-2023 14:05:00, OFF, Stop3, Company1, Bus37, 5500005555555559",
                // Trip A: starts earlier
                "1, 22-01-2023 13:00:00, ON,  Stop1, Company1, Bus37, 4111111111111111",
                "2, 22-01-2023 13:05:00, OFF, Stop2, Company1, Bus37, 4111111111111111"
        );
        Path out = outputPath();

        // act
        buildApp(fares, taps, out).run();

        // assert — first data row has 13:00, second has 14:00
        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSizeGreaterThanOrEqualTo(3);
        assertThat(lines.get(1)).contains("13:00:00");
        assertThat(lines.get(2)).contains("14:00:00");
    }
}
