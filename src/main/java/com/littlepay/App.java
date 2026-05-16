package com.littlepay;

import com.littlepay.domain.Trip;
import com.littlepay.domain.TripStatus;
import com.littlepay.io.TapReader;
import com.littlepay.io.TripWriter;
import com.littlepay.matching.TripMatcher;
import com.littlepay.pricing.FareTable;
import com.littlepay.domain.Tap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the full pipeline: read taps → match+price → sort → write trips.
 */
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private final TapReader tapReader;
    private final TripMatcher tripMatcher;
    private final TripWriter tripWriter;
    @SuppressWarnings("unused")
    private final FareTable fareTable;
    private final Path inputPath;
    private final Path outputPath;

    public App(TapReader tapReader,
               TripMatcher tripMatcher,
               TripWriter tripWriter,
               FareTable fareTable,
               Path inputPath,
               Path outputPath) {
        this.tapReader   = tapReader;
        this.tripMatcher = tripMatcher;
        this.tripWriter  = tripWriter;
        this.fareTable   = fareTable;
        this.inputPath   = inputPath;
        this.outputPath  = outputPath;
    }

    /**
     * Executes the pipeline:
     * <ol>
     *   <li>Read taps from inputPath</li>
     *   <li>Match and price taps into trips</li>
     *   <li>Sort: Started asc (nulls first) → Finished asc</li>
     *   <li>Write trips to outputPath</li>
     * </ol>
     */
    public void run() throws IOException {
        List<Tap> taps = tapReader.read(inputPath);
        log.info("{} taps read", taps.size());

        List<Trip> trips = tripMatcher.match(taps);

        // Sort: Started asc (UNMATCHED_OFF nulls first), then Finished asc
        Comparator<Trip> order = Comparator
                .<Trip, LocalDateTime>comparing(Trip::started, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Trip::finished);

        List<Trip> sorted = trips.stream().sorted(order).toList();

        tripWriter.write(sorted, outputPath);
    }
}
