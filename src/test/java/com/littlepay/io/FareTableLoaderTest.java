package com.littlepay.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.littlepay.domain.Money;
import com.littlepay.domain.StopId;
import com.littlepay.domain.StopPair;
import com.littlepay.exceptions.FareTableException;
import com.littlepay.pricing.FareTable;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FareTableLoader")
class FareTableLoaderTest {

    private static final Currency AUD = Currency.getInstance("AUD");

    @TempDir
    Path tempDir;

    private FareTable loadCsv(String content) throws IOException {
        Path file = tempDir.resolve("fares.csv");
        Files.writeString(file, content);
        return new FareTableLoader().load(file);
    }

    @Test
    @DisplayName("loads_valid_fare_table_from_csv")
    void loadsValidFareTableFromCsv() throws IOException {
        // arrange
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,3.25\nStop2,Stop3,5.50\n";
        // act
        FareTable table = loadCsv(csv);
        // assert
        Money fare = table.fareFor(new StopPair(new StopId("Stop1"), new StopId("Stop2")));
        assertThat(fare).isEqualTo(Money.of(new BigDecimal("3.25"), AUD));
    }

    @Test
    @DisplayName("loads_bundled_sample_resource")
    void loadsBundledSampleResource() throws IOException {
        // arrange — classpath resource
        Path resource = Path.of(getClass().getClassLoader().getResource("fares-sample.csv").getPath());
        // act
        FareTable table = new FareTableLoader().load(resource);
        // assert
        assertThat(table.fareFor(new StopPair(new StopId("Stop1"), new StopId("Stop2"))))
                .isEqualTo(Money.of(new BigDecimal("3.25"), AUD));
    }

    @Test
    @DisplayName("loader_rejects_negative_fare")
    void loaderRejectsNegativeFare() {
        // arrange
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,-1.00\n";
        // act + assert
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("-1.00");
    }

    @Test
    @DisplayName("loader_rejects_zero_fare")
    void loaderRejectsZeroFare() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,0.00\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("0.00");
    }

    @Test
    @DisplayName("loader_rejects_duplicate_pair_in_either_direction")
    void loaderRejectsDuplicatePairInEitherDirection() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,3.25\nStop2,Stop1,4.00\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    @DisplayName("loader_rejects_header_drift")
    void loaderRejectsHeaderDrift() {
        String csv = "From,To,Amount\nStop1,Stop2,3.25\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("header");
    }

    @Test
    @DisplayName("loader_rejects_amount_with_currency_symbol")
    void loaderRejectsAmountWithCurrencySymbol() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,$3.25\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("$3.25");
    }

    @Test
    @DisplayName("loader_rejects_amount_with_thousands_separator")
    void loaderRejectsAmountWithThousandsSeparator() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,1,000.00\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class);
    }

    @Test
    @DisplayName("loader_rejects_amount_with_more_than_two_decimal_places")
    void loaderRejectsAmountWithMoreThanTwoDecimalPlaces() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,3.256\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("3.256");
    }

    @Test
    @DisplayName("loader_rejects_amount_in_scientific_notation")
    void loaderRejectsAmountInScientificNotation() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,3.25e2\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("3.25e2");
    }

    @Test
    @DisplayName("loader_rejects_amount_with_no_decimal_places")
    void loaderRejectsAmountWithNoDecimalPlaces() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,325\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("325");
    }

    @Test
    @DisplayName("loader_strips_utf8_bom_from_fares_csv")
    void loaderStripsUtf8BomFromFaresCsv() throws IOException {
        // arrange — prepend UTF-8 BOM (EF BB BF) to a valid fares CSV
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,3.25\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + csv.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(csv, 0, content, bom.length, csv.length);

        Path file = tempDir.resolve("fares-bom.csv");
        Files.write(file, content);

        // act
        FareTable table = new FareTableLoader().load(file);

        // assert — header parsed correctly despite BOM
        Money fare = table.fareFor(new StopPair(new StopId("Stop1"), new StopId("Stop2")));
        assertThat(fare).isEqualTo(Money.of(new BigDecimal("3.25"), AUD));
    }
}
