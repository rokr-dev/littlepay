package com.littlepay.domain.pan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.littlepay.domain.Pan;
import org.junit.jupiter.api.Test;

class PanTest {

    @Test
    void maskedAmex15DigitsIsEightChars() {
        // 15-digit Amex: 378282246310005
        Pan pan = new Pan("378282246310005");
        assertThat(pan.masked()).hasSize(8);
        assertThat(pan.masked()).isEqualTo("****0005");
    }

    @Test
    void maskedVisa16DigitsIsEightChars() {
        // 16-digit Visa: 4111111111111111
        Pan pan = new Pan("4111111111111111");
        assertThat(pan.masked()).hasSize(8);
        assertThat(pan.masked()).isEqualTo("****1111");
    }

    @Test
    void maskedMaestro19DigitsIsEightChars() {
        // 19-digit Maestro: 6304000000000000001
        Pan pan = new Pan("6304000000000000001");
        assertThat(pan.masked()).hasSize(8);
        assertThat(pan.masked()).isEqualTo("****0001");
    }

    @Test
    void rejectsBlankPan() {
        assertThatThrownBy(() -> new Pan(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pan(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
