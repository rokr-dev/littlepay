package com.littlepay.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared CSV formatting constants and helpers used by readers and writers.
 */
public final class CsvFormats {

    /** Timestamp format used in tap input CSVs and trip output CSVs: {@code dd-MM-yyyy HH:mm:ss}. */
    public static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.ROOT);

    /** Unicode BOM character (U+FEFF). */
    private static final char BOM = '﻿';

    /**
     * Skips a leading UTF-8 BOM character if present in the reader.
     * The reader must support {@link BufferedReader#mark(int)}.
     */
    public static void skipBomIfPresent(BufferedReader reader) throws IOException {
        reader.mark(1);
        int firstChar = reader.read();
        if (firstChar != BOM) {
            reader.reset();
        }
    }

    private CsvFormats() {}
}
