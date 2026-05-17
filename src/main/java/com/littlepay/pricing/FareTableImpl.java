package com.littlepay.pricing;

import com.littlepay.domain.Money;
import com.littlepay.domain.StopId;
import com.littlepay.domain.StopPair;
import com.littlepay.exceptions.FareTableException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, map-backed FareTable. {@code maxFareFrom} is precomputed at construction.
 */
public final class FareTableImpl implements FareTable {

    private final Map<StopPair, Money> fares;
    private final Map<StopId, Money> maxFares;

    public FareTableImpl(Map<StopPair, Money> fares) {
        Objects.requireNonNull(fares, "fares");
        this.fares = Map.copyOf(fares);
        this.maxFares = buildMaxFares(fares);
    }

    @Override
    public Money fareFor(StopPair pair) {
        Money fare = fares.get(pair);
        if (fare == null) {
            throw new FareTableException(
                    "No fare for stop pair: " + pair.first().value() + " <-> " + pair.second().value());
        }
        return fare;
    }

    @Override
    public Money maxFareFrom(StopId stop) {
        Money max = maxFares.get(stop);
        if (max == null) {
            throw new FareTableException(
                    "No routes found from stop: " + stop.value());
        }
        return max;
    }

    /**
     * Precomputes the maximum fare touching each stop.
     * A stop "touches" a pair if it is either the first or second stop.
     */
    private static Map<StopId, Money> buildMaxFares(Map<StopPair, Money> fares) {
        Map<StopId, Money> result = new HashMap<>();
        for (Map.Entry<StopPair, Money> entry : fares.entrySet()) {
            StopPair pair = entry.getKey();
            Money fare = entry.getValue();
            updateMax(result, pair.first(), fare);
            updateMax(result, pair.second(), fare);
        }
        return Map.copyOf(result);
    }

    private static void updateMax(Map<StopId, Money> map, StopId stop, Money fare) {
        map.merge(stop, fare, (existing, candidate) -> {
            // Compare BigDecimal amounts
            return existing.amount().compareTo(candidate.amount()) >= 0 ? existing : candidate;
        });
    }
}
