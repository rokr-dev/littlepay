package com.littlepay.domain;

import java.util.Objects;

/**
 * An unordered pair of stops. (A,B) equals (B,A).
 * Canonical form: lexicographically smaller stop stored as {@code first}.
 */
public final class StopPair {

    private final StopId first;
    private final StopId second;

    public StopPair(StopId a, StopId b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        // Normalise: lexicographically smaller value goes first
        if (a.value().compareTo(b.value()) <= 0) {
            this.first = a;
            this.second = b;
        } else {
            this.first = b;
            this.second = a;
        }
    }

    public StopId first() {
        return first;
    }

    public StopId second() {
        return second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StopPair sp)) {
            return false;
        }
        return first.equals(sp.first) && second.equals(sp.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public String toString() {
        return "StopPair(" + first.value() + ", " + second.value() + ")";
    }
}
