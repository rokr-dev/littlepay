package com.littlepay.io;

import com.littlepay.domain.Tap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Port: reads a sequence of {@link Tap} events from a source.
 */
public interface TapReader {

    /**
     * Reads all tap events from the given file.
     *
     * @param path path to the CSV file
     * @return immutable list of taps in file order
     * @throws com.littlepay.TapHeaderException if the CSV header does not match the expected schema
     * @throws com.littlepay.TapRowException    if any row is malformed
     * @throws com.littlepay.InputFileException  if the file is missing or unreadable
     * @throws IOException                       on unexpected I/O errors
     */
    List<Tap> read(Path path) throws IOException;
}
