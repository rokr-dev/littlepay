package com.littlepay.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StopIdTest {

    @Test
    void rejectsBlankStopId() {
        assertThatThrownBy(() -> new StopId(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StopId("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StopId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
