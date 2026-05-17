package com.littlepay.matching;

import com.littlepay.domain.Tap;
import com.littlepay.domain.Trip;
import java.util.List;

/**
 * Port: converts a list of raw tap events into priced trip records.
 */
public interface TripMatcher {

    /**
     * Match and price the given taps.
     *
     * @param taps unordered raw tap events
     * @return priced trip records
     */
    List<Trip> match(List<Tap> taps);
}
