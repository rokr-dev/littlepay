package com.littlepay.io;

import com.littlepay.exceptions.FareTableException;
import com.littlepay.domain.Money;
import com.littlepay.domain.StopId;
import com.littlepay.domain.StopPair;
import com.littlepay.pricing.FareTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void loads_valid_fare_table_from_csv() throws IOException {
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
    void loads_bundled_sample_resource() throws IOException {
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
    void loader_rejects_negative_fare() {
        // arrange
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,-1.00\n";
        // act + assert
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("-1.00");
    }

    @Test
    @DisplayName("loader_rejects_zero_fare")
    void loader_rejects_zero_fare() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,0.00\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("0.00");
    }

    @Test
    @DisplayName("loader_rejects_duplicate_pair_in_either_direction")
    void loader_rejects_duplicate_pair_in_either_direction() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,3.25\nStop2,Stop1,4.00\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    @DisplayName("loader_rejects_header_drift")
    void loader_rejects_header_drift() {
        String csv = "From,To,Amount\nStop1,Stop2,3.25\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("header");
    }

    @Test
    @DisplayName("loader_rejects_amount_with_currency_symbol")
    void loader_rejects_amount_with_currency_symbol() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,$3.25\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("$3.25");
    }

    @Test
    @DisplayName("loader_rejects_amount_with_thousands_separator")
    void loader_rejects_amount_with_thousands_separator() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,1,000.00\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class);
    }

    @Test
    @DisplayName("loader_rejects_amount_with_more_than_two_decimal_places")
    void loader_rejects_amount_with_more_than_two_decimal_places() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,3.256\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("3.256");
    }

    @Test
    @DisplayName("loader_rejects_amount_in_scientific_notation")
    void loader_rejects_amount_in_scientific_notation() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,3.25e2\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("3.25e2");
    }

    @Test
    @DisplayName("loader_rejects_amount_with_no_decimal_places")
    void loader_rejects_amount_with_no_decimal_places() {
        String csv = "FromStopId,ToStopId,Amount\nStop1,Stop2,325\n";
        assertThatThrownBy(() -> loadCsv(csv))
                .isInstanceOf(FareTableException.class)
                .hasMessageContaining("325");
    }
}
