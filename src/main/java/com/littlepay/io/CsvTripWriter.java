package com.littlepay.io;

import com.littlepay.domain.Trip;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * CSV adapter for {@link TripWriter}.
 *
 * <p>Header: {@code Started,Finished,DurationSecs,FromStopId,ToStopId,ChargeAmount,CompanyId,BusID,PAN,Status}
 * <p>Timestamps: {@code dd-MM-yyyy HH:mm:ss} at UTC, {@link Locale#ROOT}.
 * <p>Money: {@code Money.format()} → e.g. {@code $3.25}.
 * <p>Null fields emit empty cells: {@code started}/{@code fromStop} for UNMATCHED_OFF; {@code finished}/{@code toStop} for INCOMPLETE.
 * <p>Parent directory created via {@link Files#createDirectories} if missing.
 * <p>Existing file overwritten; WARN logged.
 */
public final class CsvTripWriter implements TripWriter {

    private static final Logger log = LoggerFactory.getLogger(CsvTripWriter.class);

    static final String[] HEADER = {
            "Started", "Finished", "DurationSecs",
            "FromStopId", "ToStopId", "ChargeAmount",
            "CompanyId", "BusID", "PAN", "Status"
    };

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);

    @Override
    public void write(List<Trip> trips, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.exists(path)) {
            log.warn("Output file already exists and will be overwritten: {}", path);
        }

        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader(HEADER)
                .build();

        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, format)) {

            for (Trip trip : trips) {
                printer.printRecord(
                        trip.started() != null
                                ? TIMESTAMP_FMT.format(trip.started().atOffset(ZoneOffset.UTC))
                                : "",
                        trip.finished() != null
                                ? TIMESTAMP_FMT.format(trip.finished().atOffset(ZoneOffset.UTC))
                                : "",
                        trip.durationSecs(),
                        trip.fromStop() != null ? trip.fromStop().value() : "",
                        trip.toStop() != null ? trip.toStop().value() : "",
                        trip.chargeAmount().format(),
                        trip.companyId(),
                        trip.busId(),
                        trip.pan().value(),
                        trip.status().name()
                );
            }
        }
    }
}
