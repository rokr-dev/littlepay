package com.littlepay.io;

import com.littlepay.domain.Pan;
import com.littlepay.domain.StopId;
import com.littlepay.domain.Tap;
import com.littlepay.domain.TapType;
import com.littlepay.exceptions.InputFileException;
import com.littlepay.exceptions.TapHeaderException;
import com.littlepay.exceptions.TapRowException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter: reads tap events from a UTF-8 CSV file (BOM optional, CRLF or LF).
 *
 * <p>Expected header (exact, space after comma): {@code ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN}
 * <p>Timestamp format: {@code dd-MM-yyyy HH:mm:ss} at UTC.
 * <p>TapType parsed case-insensitively after strip.
 * <p>Blank lines silently skipped. Trailing/leading whitespace in fields stripped.
 * <p>Duplicate IDs fail fast with exit code 4 ({@link TapRowException}).
 */
public final class CsvTapReader implements TapReader {

    private static final Logger log = LoggerFactory.getLogger(CsvTapReader.class);

    private static final DateTimeFormatter TIMESTAMP_FMT = CsvFormats.TIMESTAMP;

    /** Expected column names — with the single space after comma as in the spec. */
    private static final List<String> EXPECTED_HEADERS =
            List.of("ID", "DateTimeUTC", "TapType", "StopId", "CompanyId", "BusID", "PAN");

    @Override
    public List<Tap> read(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            CsvFormats.skipBomIfPresent(reader);
            return parse(reader, path.toString());
        } catch (IOException e) {
            log.error("failed to open tap file: {}", path, e);
            throw new InputFileException("Tap file not found or unreadable: " + path, e);
        }
    }

    // -----------------------------------------------------------------------
    // internal
    // -----------------------------------------------------------------------

    private List<Tap> parse(Reader reader, String sourceName) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .build();

        try (CSVParser parser = CSVParser.parse(reader, format)) {
            validateHeader(parser.getHeaderNames(), sourceName);

            List<Tap> taps = new ArrayList<>();
            Set<Long> seenIds = new HashSet<>();

            for (CSVRecord record : parser) {
                // Guard for any blank record slipping through
                if (isBlankRecord(record)) {
                    log.debug("Skipping blank line at record {} in {}", record.getRecordNumber(), sourceName);
                    continue;
                }

                long rowNum = record.getRecordNumber();
                taps.add(parseRecord(record, rowNum, seenIds, sourceName));
            }

            return List.copyOf(taps);
        }
    }

    private void validateHeader(List<String> actual, String sourceName) {
        if (!actual.equals(EXPECTED_HEADERS)) {
            throw new TapHeaderException(
                    "Tap CSV header mismatch in " + sourceName
                            + ": expected " + EXPECTED_HEADERS
                            + " but got " + actual);
        }
    }

    private Tap parseRecord(CSVRecord record, long rowNum, Set<Long> seenIds, String sourceName) {
        // ID
        String idStr = record.get("ID").strip();
        long id;
        try {
            id = Long.parseLong(idStr, 10);
        } catch (NumberFormatException e) {
            log.error("invalid tap ID '{}' at row {} in {}", idStr, rowNum, sourceName, e);
            throw new TapRowException(
                    "Invalid ID '" + idStr + "' at row " + rowNum + " in " + sourceName, e);
        }

        if (!seenIds.add(id)) {
            throw new TapRowException(
                    "Duplicate tap ID " + id + " at row " + rowNum + " in " + sourceName);
        }

        // Timestamp
        String tsStr = record.get("DateTimeUTC").strip();
        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(tsStr, TIMESTAMP_FMT);
        } catch (DateTimeParseException e) {
            log.error("invalid timestamp '{}' at row {} in {}", tsStr, rowNum, sourceName, e);
            throw new TapRowException(
                    "Invalid timestamp '" + tsStr + "' at row " + rowNum + " in " + sourceName
                            + " — expected dd-MM-yyyy HH:mm:ss", e);
        }

        // TapType — case-insensitive after strip
        String tapTypeStr = record.get("TapType").strip();
        TapType tapType;
        try {
            tapType = TapType.valueOf(tapTypeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.error("invalid TapType '{}' at row {} in {}", tapTypeStr, rowNum, sourceName, e);
            throw new TapRowException(
                    "Invalid TapType '" + tapTypeStr + "' at row " + rowNum + " in " + sourceName
                            + " — expected ON or OFF", e);
        }

        // Remaining fields
        StopId stopId  = new StopId(record.get("StopId").strip());
        String companyId = record.get("CompanyId").strip();
        String busId     = record.get("BusID").strip();
        Pan pan          = new Pan(record.get("PAN").strip());

        return new Tap(id, dateTime, tapType, stopId, companyId, busId, pan);
    }

    /** True if every field in the record is blank after strip. */
    private boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (!value.strip().isEmpty()) {
                return false;
            }
        }
        return true;
    }

}
