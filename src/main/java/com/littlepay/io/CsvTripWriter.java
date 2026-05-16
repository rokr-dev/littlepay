package com.littlepay.io;

import com.littlepay.domain.Trip;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV adapter for {@link TripWriter}.
 *
 * <p>Header: {@code Started,Finished,DurationSecs,FromStopId,ToStopId,ChargeAmount,CompanyId,BusID,PAN,Status}
 * <p>Timestamps: {@code dd-MM-yyyy HH:mm:ss} at UTC, {@link Locale#ROOT}.
 * <p>Money: {@code Money.format()} → e.g. {@code $3.25}.
 * <p>Null fields emit empty cells: {@code started}/{@code fromStop} for UNMATCHED_OFF; {@code finished}/{@code toStop} for INCOMPLETE.
 * <p>Parent directory created via {@link Files#createDirectories} if missing.
 * <p>Existing file silently overwritten.
 */
public final class CsvTripWriter implements TripWriter {

    static final String[] HEADER = {
            "Started", "Finished", "DurationSecs",
            "FromStopId", "ToStopId", "ChargeAmount",
            "CompanyId", "BusID", "PAN", "Status"
    };

    private static final DateTimeFormatter TIMESTAMP_FMT =
            CsvFormats.TIMESTAMP.withZone(ZoneOffset.UTC);

    @Override
    public void write(List<Trip> trips, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader(HEADER)
                .setRecordSeparator("\n")
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
