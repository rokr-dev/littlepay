package com.littlepay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency AUD = Currency.getInstance("AUD");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void scaleEnforcedAtConstruction() {
        Money m = Money.of(new BigDecimal("1.5"), AUD);
        assertThat(m.amount().scale()).isEqualTo(2);
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("1.50"));
    }

    @Test
    void roundsHalfUp() {
        // 1.005 with HALF_UP → 1.01
        Money m = Money.of(new BigDecimal("1.005"), AUD);
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("1.01"));
    }

    @Test
    void rejectsCurrencyMismatchOnArithmetic() {
        Money aud = Money.of(new BigDecimal("1.00"), AUD);
        Money usd = Money.of(new BigDecimal("2.00"), USD);
        assertThatThrownBy(() -> aud.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void formatEmitsDollarXxXxLocaleRoot() {
        Money m = Money.of(new BigDecimal("3.50"), AUD);
        assertThat(m.format()).isEqualTo("$3.50");
    }
}
