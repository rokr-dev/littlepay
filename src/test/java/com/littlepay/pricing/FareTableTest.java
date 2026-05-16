package com.littlepay.pricing;

import com.littlepay.FareTableException;
import com.littlepay.domain.Money;
import com.littlepay.domain.StopId;
import com.littlepay.domain.StopPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FareTable")
class FareTableTest {

    private static final Currency AUD = Currency.getInstance("AUD");

    private static final StopId STOP1 = new StopId("Stop1");
    private static final StopId STOP2 = new StopId("Stop2");
    private static final StopId STOP3 = new StopId("Stop3");

    private static final Money FARE_1_2 = Money.of(new BigDecimal("3.25"), AUD);
    private static final Money FARE_2_3 = Money.of(new BigDecimal("5.50"), AUD);
    private static final Money FARE_1_3 = Money.of(new BigDecimal("7.30"), AUD);

    private FareTable fareTable;

    @BeforeEach
    void setUp() {
        Map<StopPair, Money> fares = new HashMap<>();
        fares.put(new StopPair(STOP1, STOP2), FARE_1_2);
        fares.put(new StopPair(STOP2, STOP3), FARE_2_3);
        fares.put(new StopPair(STOP1, STOP3), FARE_1_3);
        fareTable = new FareTableImpl(fares);
    }

    @Nested
    @DisplayName("fareFor")
    class FareForTests {

        @Test
        @DisplayName("charges_completed_trip_per_fare_table")
        void charges_completed_trip_per_fare_table() {
            // arrange
            StopPair pair = new StopPair(STOP1, STOP2);
            // act
            Money result = fareTable.fareFor(pair);
            // assert
            assertThat(result).isEqualTo(FARE_1_2);
        }

        @Test
        @DisplayName("treats_fare_pair_as_unordered")
        void treats_fare_pair_as_unordered() {
            // arrange - reversed order of stops
            StopPair pair = new StopPair(STOP2, STOP1);
            // act
            Money result = fareTable.fareFor(pair);
            // assert
            assertThat(result).isEqualTo(FARE_1_2);
        }

        @Test
        @DisplayName("throws_unknown_stop_when_pair_not_in_table")
        void throws_unknown_stop_when_pair_not_in_table() {
            // arrange
            StopId unknown = new StopId("StopX");
            StopPair pair = new StopPair(STOP1, unknown);
            // act + assert
            assertThatThrownBy(() -> fareTable.fareFor(pair))
                    .isInstanceOf(FareTableException.class)
                    .hasMessageContaining("StopX");
        }
    }

    @Nested
    @DisplayName("maxFareFrom")
    class MaxFareFromTests {

        @Test
        @DisplayName("max_fare_from_origin_stop_returns_largest_touching_fare")
        void max_fare_from_origin_stop_returns_largest_touching_fare() {
            // arrange - Stop1 touches Stop2 ($3.25) and Stop3 ($7.30); max is $7.30
            // act
            Money result = fareTable.maxFareFrom(STOP1);
            // assert
            assertThat(result).isEqualTo(FARE_1_3);
        }

        @Test
        @DisplayName("max_fare_from_stop2_returns_largest_touching_fare")
        void max_fare_from_stop2_returns_largest_touching_fare() {
            // Stop2 touches Stop1 ($3.25) and Stop3 ($5.50); max is $5.50
            Money result = fareTable.maxFareFrom(STOP2);
            assertThat(result).isEqualTo(FARE_2_3);
        }

        @Test
        @DisplayName("max_fare_from_stop_with_single_route_returns_that_fare")
        void max_fare_from_stop_with_single_route_returns_that_fare() {
            // Stop3 touches Stop2 ($5.50) and Stop1 ($7.30); max is $7.30
            Money result = fareTable.maxFareFrom(STOP3);
            assertThat(result).isEqualTo(FARE_1_3);
        }
    }
}
