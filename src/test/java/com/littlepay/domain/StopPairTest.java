package com.littlepay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StopPairTest {

    @Test
    void normalisesUnorderedPair() {
        StopPair ab = new StopPair(new StopId("Stop1"), new StopId("Stop2"));
        StopPair ba = new StopPair(new StopId("Stop2"), new StopId("Stop1"));
        assertThat(ab).isEqualTo(ba);
    }

    @Test
    void equalsAndHashCodeContract() {
        StopPair p1 = new StopPair(new StopId("A"), new StopId("B"));
        StopPair p2 = new StopPair(new StopId("B"), new StopId("A"));
        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }
}
