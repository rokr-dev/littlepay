package com.littlepay.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.littlepay.domain.Tap;
import com.littlepay.domain.TapType;
import com.littlepay.exceptions.TapHeaderException;
import com.littlepay.exceptions.TapRowException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("CsvTapReader")
class CsvTapReaderTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private List<Tap> readCsv(String content) throws IOException {
        Path file = tempDir.resolve("taps.csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return new CsvTapReader().read(file);
    }

    private Path resourceFile(String name) {
        return Path.of(getClass().getClassLoader().getResource(name).getPath());
    }

    // -----------------------------------------------------------------------
    // happy-path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("input_with_utf8_bom_is_accepted_and_header_validated_after_strip")
    void inputWithUtf8BomIsAcceptedAndHeaderValidatedAfterStrip() throws IOException {
        // arrange — BOM file written by build (binary resource)
        Path file = resourceFile("taps-bom.csv");
        // act
        List<Tap> taps = new CsvTapReader().read(file);
        // assert
        assertThat(taps).hasSize(1);
        assertThat(taps.getFirst().id()).isEqualTo(1L);
        assertThat(taps.getFirst().tapType()).isEqualTo(TapType.ON);
    }

    @Test
    @DisplayName("input_with_crlf_line_endings_is_accepted")
    void inputWithCrlfLineEndingsIsAccepted() throws IOException {
        // arrange
        Path file = resourceFile("taps-crlf.csv");
        // act
        List<Tap> taps = new CsvTapReader().read(file);
        // assert
        assertThat(taps).hasSize(1);
        assertThat(taps.getFirst().stopId().value()).isEqualTo("Stop1");
    }

    @Test
    @DisplayName("input_with_trailing_whitespace_in_fields_is_tolerated")
    void inputWithTrailingWhitespaceInFieldsIsTolerated() throws IOException {
        // arrange
        Path file = resourceFile("taps-whitespace.csv");
        // act
        List<Tap> taps = new CsvTapReader().read(file);
        // assert
        assertThat(taps).hasSize(1);
        assertThat(taps.getFirst().id()).isEqualTo(1L);
        assertThat(taps.getFirst().companyId()).isEqualTo("Company1");
        assertThat(taps.getFirst().busId()).isEqualTo("Bus37");
    }

    @Test
    @DisplayName("blank_lines_skipped")
    void blankLinesSkipped() throws IOException {
        // arrange — two data rows with a blank line between them
        String csv = """
                ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN
                1, 22-01-2023 13:00:00, ON, Stop1, Company1, Bus37, 5500005555555559
                
                2, 22-01-2023 13:05:00, OFF, Stop2, Company1, Bus37, 5500005555555559
                """;
        // act
        List<Tap> taps = readCsv(csv);
        // assert
        assertThat(taps).hasSize(2);
    }

    @Test
    @DisplayName("tap_type_parses_case_insensitively")
    void tapTypeParsesCaseInsensitively() throws IOException {
        // arrange — lowercase "on" and mixed-case "Off"
        String csv = """
                ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN
                1, 22-01-2023 13:00:00, on, Stop1, Company1, Bus37, 5500005555555559
                2, 22-01-2023 13:05:00, Off, Stop2, Company1, Bus37, 5500005555555559
                """;
        // act
        List<Tap> taps = readCsv(csv);
        // assert
        assertThat(taps.getFirst().tapType()).isEqualTo(TapType.ON);
        assertThat(taps.get(1).tapType()).isEqualTo(TapType.OFF);
    }

    @Test
    @DisplayName("timestamp_parsed_as_utc_with_correct_date_time")
    void timestampParsedAsUtcWithCorrectDateTime() throws IOException {
        // arrange
        String csv = """
                ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN
                1, 22-01-2023 13:00:00, ON, Stop1, Company1, Bus37, 5500005555555559
                """;
        // act
        List<Tap> taps = readCsv(csv);
        // assert
        assertThat(taps.getFirst().dateTime()).isEqualTo(LocalDateTime.of(2023, 1, 22, 13, 0, 0));
    }

    // -----------------------------------------------------------------------
    // error paths
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("header_mismatch_throws_with_exit_code_three")
    void headerMismatchThrowsWithExitCodeThree() {
        // arrange
        Path file = resourceFile("taps-bad-header.csv");
        // act / assert
        assertThatThrownBy(() -> new CsvTapReader().read(file))
                .isInstanceOf(TapHeaderException.class)
                .satisfies(e -> assertThat(((TapHeaderException) e).getExitCode()).isEqualTo(3));
    }

    @Test
    @DisplayName("bad_tap_type_throws_with_exit_code_four")
    void badTapTypeThrowsWithExitCodeFour() {
        // arrange
        Path file = resourceFile("taps-bad-type.csv");
        // act / assert
        assertThatThrownBy(() -> new CsvTapReader().read(file))
                .isInstanceOf(TapRowException.class)
                .satisfies(e -> assertThat(((TapRowException) e).getExitCode()).isEqualTo(4))
                .hasMessageContaining("row 1");
    }

    @Test
    @DisplayName("duplicate_id_throws_with_exit_code_four")
    void duplicateIdThrowsWithExitCodeFour() {
        // arrange
        Path file = resourceFile("taps-duplicate-id.csv");
        // act / assert
        assertThatThrownBy(() -> new CsvTapReader().read(file))
                .isInstanceOf(TapRowException.class)
                .satisfies(e -> assertThat(((TapRowException) e).getExitCode()).isEqualTo(4))
                .hasMessageContaining("row 2");
    }
}
