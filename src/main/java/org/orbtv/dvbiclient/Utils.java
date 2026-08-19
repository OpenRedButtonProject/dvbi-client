package org.orbtv.dvbiclient;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public class Utils {
    public static Long getSecondsFromDate(String dateString) {
        if (dateString != null) {
            try {
                Instant instant = Instant.parse(dateString.trim());
                return instant.getEpochSecond();
            } catch (DateTimeParseException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static Integer getSecondsFromTime(String timeString) {
        if (timeString != null) {
            try {
                String[] parts = timeString.split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2].substring(0, 2)); // Remove the 'Z' character
                return hours * 3600 + minutes * 60 + seconds;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static Duration getDurationFromString(String duration) {
        if (duration == null) {
            return null;
        }
        try {
            return Duration.parse(duration.trim());
        } catch (DateTimeParseException e) {
            e.printStackTrace();
            return null;
        }
    }
}
