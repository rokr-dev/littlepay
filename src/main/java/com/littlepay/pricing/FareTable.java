package com.littlepay.pricing;

import com.littlepay.domain.Money;
import com.littlepay.domain.StopId;
import com.littlepay.domain.StopPair;

/**
 * Read-only view of the fare matrix.
 */
public interface FareTable {

    /**
     * Returns the fare for the given stop pair.
     *
     * @throws com.littlepay.FareTableException if the pair is not in the table
     */
    Money fareFor(StopPair pair);

    /**
     * Returns the maximum fare touching the given stop (used for INCOMPLETE trips).
     *
     * @throws com.littlepay.FareTableException if the stop has no routes in the table
     */
    Money maxFareFrom(StopId stop);
}
