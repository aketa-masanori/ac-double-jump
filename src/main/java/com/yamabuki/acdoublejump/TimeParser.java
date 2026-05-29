package com.yamabuki.acdoublejump;

import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser {
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)\\s*(ms|s|m|h)?$");

    private TimeParser() {
    }

    public static long parseMillis(String input, long fallbackMillis, Logger logger, String pathForLog) {
        if (input == null) {
            return fallbackMillis;
        }

        Matcher matcher = DURATION_PATTERN.matcher(input.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            logger.warning(() -> "Invalid duration at " + pathForLog + ": " + input + ", using fallback.");
            return fallbackMillis;
        }

        long value;
        try {
            value = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            logger.warning(() -> "Invalid number at " + pathForLog + ": " + input + ", using fallback.");
            return fallbackMillis;
        }

        String unit = matcher.group(2);
        if (unit == null || unit.equals("s")) {
            return value * 1000L;
        }
        if (unit.equals("ms")) {
            return value;
        }
        if (unit.equals("m")) {
            return value * 60_000L;
        }
        if (unit.equals("h")) {
            return value * 3_600_000L;
        }

        logger.warning(() -> "Unknown duration unit at " + pathForLog + ": " + input + ", using fallback.");
        return fallbackMillis;
    }
}
