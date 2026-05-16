package com.littlepay.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StopPairTest {

    @Test
    void normalises_unordered_pair() {
        StopPair ab = new StopPair(new StopId("Stop1"), new StopId("Stop2"));
        StopPair ba = new StopPair(new StopId("Stop2"), new StopId("Stop1"));
        assertThat(ab).isEqualTo(ba);
    }

    @Test
    void equals_and_hashCode_contract() {
        StopPair p1 = new StopPair(new StopId("A"), new StopId("B"));
        StopPair p2 = new StopPair(new StopId("B"), new StopId("A"));
        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }
}
