package com.littlepay.io;

import com.littlepay.domain.Trip;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Port: writes a list of {@link Trip} records to a file.
 */
public interface TripWriter {

    /**
     * Writes {@code trips} to the given {@code path}.
     *
     * <ul>
     *   <li>Parent directory is created if missing (via {@code Files.createDirectories}).
     *   <li>Existing file is overwritten; a WARN log line is recorded.
     * </ul>
     *
     * @param trips ordered list of trips to write
     * @param path  destination file path
     * @throws IOException on I/O failure
     */
    void write(List<Trip> trips, Path path) throws IOException;
}
