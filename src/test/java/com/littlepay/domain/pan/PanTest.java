package com.littlepay.domain.pan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PanTest {

    @Test
    void masked_amex_15_digits_is_eight_chars() {
        // 15-digit Amex: 378282246310005
        Pan pan = new Pan("378282246310005");
        assertThat(pan.masked()).hasSize(8);
        assertThat(pan.masked()).isEqualTo("****0005");
    }

    @Test
    void masked_visa_16_digits_is_eight_chars() {
        // 16-digit Visa: 4111111111111111
        Pan pan = new Pan("4111111111111111");
        assertThat(pan.masked()).hasSize(8);
        assertThat(pan.masked()).isEqualTo("****1111");
    }

    @Test
    void masked_maestro_19_digits_is_eight_chars() {
        // 19-digit Maestro: 6304000000000000001
        Pan pan = new Pan("6304000000000000001");
        assertThat(pan.masked()).hasSize(8);
        assertThat(pan.masked()).isEqualTo("****0001");
    }

    @Test
    void rejects_blank_pan() {
        assertThatThrownBy(() -> new Pan(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pan(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
