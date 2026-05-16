package com.littlepay.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StopIdTest {

    @Test
    void rejects_blank_stop_id() {
        assertThatThrownBy(() -> new StopId(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StopId("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StopId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
