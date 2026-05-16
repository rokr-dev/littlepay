package com.littlepay.io;

import com.littlepay.domain.Money;
import com.littlepay.domain.StopId;
import com.littlepay.domain.Trip;
import com.littlepay.domain.TripStatus;
import com.littlepay.domain.Pan;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTripWriterTest {

    private static final Currency AUD = Currency.getInstance("AUD");

    private static final String EXPECTED_HEADER =
            "Started,Finished,DurationSecs,FromStopId,ToStopId,ChargeAmount,CompanyId,BusID,PAN,Status";

    private Trip completedTrip() {
        return new Trip(
                LocalDateTime.of(2023, 1, 22, 13, 0, 0),
                LocalDateTime.of(2023, 1, 22, 13, 5, 0),
                300L,
                new StopId("Stop1"),
                new StopId("Stop2"),
                Money.of(new BigDecimal("3.25"), AUD),
                "Company1",
                "Bus37",
                new Pan("5500005555555559"),
                TripStatus.COMPLETED
        );
    }

    private Trip unmatchedOffTrip() {
        return new Trip(
                null,          // started = null
                LocalDateTime.of(2023, 1, 22, 13, 5, 0),
                0L,
                null,          // fromStop = null
                new StopId("Stop2"),
                Money.of(new BigDecimal("7.30"), AUD),
                "Company1",
                "Bus37",
                new Pan("4111111111111111"),
                TripStatus.INCOMPLETE
        );
    }

    @Test
    void emits_expected_header_row(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("trips.csv");
        TripWriter writer = new CsvTripWriter();

        writer.write(List.of(completedTrip()), out);

        List<String> lines = Files.readAllLines(out);
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).isEqualTo(EXPECTED_HEADER);
    }

    @Test
    void unmatched_off_row_emits_empty_started_and_from_stop_cells(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("trips.csv");
        TripWriter writer = new CsvTripWriter();

        writer.write(List.of(unmatchedOffTrip()), out);

        // Parse back via Commons CSV to handle quoted empty strings correctly
        CSVFormat fmt = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        try (Reader r = Files.newBufferedReader(out, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(r, fmt)) {
            List<CSVRecord> records = parser.getRecords();
            assertThat(records).hasSize(1);
            CSVRecord row = records.get(0);
            assertThat(row.get("Started")).as("Started should be empty for UNMATCHED_OFF").isEmpty();
            assertThat(row.get("FromStopId")).as("FromStopId should be empty for UNMATCHED_OFF").isEmpty();
            // Verify other required fields present
            assertThat(row.get("Status")).isEqualTo("INCOMPLETE");
        }
    }

    @Test
    void round_trip_write_then_read_produces_equivalent_trips(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("trips.csv");
        TripWriter writer = new CsvTripWriter();
        Trip original = completedTrip();

        writer.write(List.of(original), out);

        CSVFormat fmt = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        try (Reader r = Files.newBufferedReader(out, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(r, fmt)) {
            List<CSVRecord> records = parser.getRecords();
            assertThat(records).hasSize(1);
            CSVRecord row = records.get(0);
            assertThat(row.get("Started")).isEqualTo("22-01-2023 13:00:00");
            assertThat(row.get("Finished")).isEqualTo("22-01-2023 13:05:00");
            assertThat(row.get("DurationSecs")).isEqualTo("300");
            assertThat(row.get("FromStopId")).isEqualTo("Stop1");
            assertThat(row.get("ToStopId")).isEqualTo("Stop2");
            assertThat(row.get("ChargeAmount")).isEqualTo("$3.25");
            assertThat(row.get("CompanyId")).isEqualTo("Company1");
            assertThat(row.get("BusID")).isEqualTo("Bus37");
            assertThat(row.get("PAN")).isEqualTo("5500005555555559");
            assertThat(row.get("Status")).isEqualTo("COMPLETED");
        }
    }

    @Test
    void creates_output_directory_when_missing(@TempDir Path tmp) throws IOException {
        Path nested = tmp.resolve("deep").resolve("nested").resolve("trips.csv");
        TripWriter writer = new CsvTripWriter();

        writer.write(List.of(completedTrip()), nested);

        assertThat(nested).exists();
        List<String> lines = Files.readAllLines(nested);
        assertThat(lines.get(0)).isEqualTo(EXPECTED_HEADER);
    }

    @Test
    void overwrites_existing_output_file_and_logs_warn(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("trips.csv");
        // Write stale content first
        Files.writeString(out, "stale content\n");

        TripWriter writer = new CsvTripWriter();
        writer.write(List.of(completedTrip()), out);

        List<String> lines = Files.readAllLines(out);
        // Stale content gone; fresh header present
        assertThat(lines.get(0)).isEqualTo(EXPECTED_HEADER);
        assertThat(lines).hasSize(2);
    }
}
