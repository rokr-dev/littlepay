package com.littlepay.io;

import com.littlepay.exceptions.FareTableException;
import com.littlepay.exceptions.InputFileException;
import com.littlepay.domain.Money;
import com.littlepay.domain.StopId;
import com.littlepay.domain.StopPair;
import com.littlepay.pricing.FareTable;
import com.littlepay.pricing.FareTableImpl;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads and strictly validates a fare CSV file into a {@link FareTable}.
 *
 * <p>Expected header: {@code FromStopId,ToStopId,Amount}
 * <p>Amount format: {@code ^\d+\.\d{2}$} — digits, period, exactly two decimal digits.
 * Any deviation fails fast with exit code 6 ({@link FareTableException}).
 */
public final class FareTableLoader {

    private static final Currency AUD = Currency.getInstance("AUD");

    /** Amount must match exactly: one or more digits, a period, exactly two digits. */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d+\\.\\d{2}$");

    private static final List<String> EXPECTED_HEADERS =
            List.of("FromStopId", "ToStopId", "Amount");

    /** Bundled classpath resource — shipped inside the JAR. */
    static final String BUNDLED_RESOURCE = "/fares.csv";

    public FareTable load(Path path) {
        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new InputFileException("Fare file not found or unreadable: " + path);
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            CsvFormats.skipBomIfPresent(reader);
            return parse(reader, path.toString());
        } catch (IOException e) {
            throw new InputFileException("Failed to read fare file: " + path, e);
        }
    }

    /**
     * Loads the bundled {@code fares.csv} from the JAR classpath.
     * Used by {@link com.littlepay.Main} when {@code --fares} is not supplied.
     */
    public FareTable loadBundled() {
        InputStream stream = FareTableLoader.class.getResourceAsStream(BUNDLED_RESOURCE);
        if (stream == null) {
            throw new FareTableException(
                    "Bundled fares.csv not found in JAR — this is a packaging error");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parse(reader, "classpath:" + BUNDLED_RESOURCE);
        } catch (IOException e) {
            throw new FareTableException("Failed to read bundled fares.csv", e);
        }
    }

    private FareTable parse(Reader reader, String sourceName) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .build();

        try (CSVParser parser = CSVParser.parse(reader, format)) {
            // Validate header immediately after parsing
            validateHeaders(parser.getHeaderNames(), sourceName);

            Map<StopPair, Money> fares = new HashMap<>();

            for (CSVRecord record : parser) {
                String fromStop = record.get("FromStopId").strip();
                String toStop = record.get("ToStopId").strip();
                String amountStr = record.get("Amount").strip();

                validateAmount(amountStr, record.getRecordNumber(), sourceName);

                BigDecimal amount = new BigDecimal(amountStr);

                // Must be positive (> 0) — validated by pattern (no negatives) + explicit zero check
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new FareTableException(
                            "Fare amount must be positive, got: " + amountStr
                                    + " at row " + record.getRecordNumber() + " in " + sourceName);
                }

                StopPair pair = new StopPair(new StopId(fromStop), new StopId(toStop));

                if (fares.containsKey(pair)) {
                    throw new FareTableException(
                            "duplicate stop pair (in either direction): "
                                    + fromStop + " <-> " + toStop
                                    + " at row " + record.getRecordNumber() + " in " + sourceName);
                }

                fares.put(pair, Money.of(amount, AUD));
            }

            return new FareTableImpl(fares);
        }
    }

    /**
     * Validates that the parsed headers exactly match the expected column names.
     */
    private void validateHeaders(List<String> actualHeaders, String sourceName) {
        if (!actualHeaders.equals(EXPECTED_HEADERS)) {
            throw new FareTableException(
                    "Fare CSV header drift in " + sourceName
                            + ": expected " + EXPECTED_HEADERS
                            + " but got " + actualHeaders);
        }
    }

    /**
     * Validates amount string against the strict pattern: digits, period, exactly two digits.
     * Rejects: currency symbols, thousands separators, scientific notation, no decimals, >2 dp.
     */
    private void validateAmount(String amountStr, long rowNumber, String sourceName) {
        if (!AMOUNT_PATTERN.matcher(amountStr).matches()) {
            throw new FareTableException(
                    "Invalid fare amount: '" + amountStr + "' at row " + rowNumber
                            + " in " + sourceName
                            + " — must match \\d+\\.\\d{2} (e.g. 3.25)");
        }
    }
}
