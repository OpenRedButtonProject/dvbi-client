package org.orbtv.dvbiclient;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static Long getSecondsFromDate(String dateString) {
        if (dateString != null) {
            try {
                Instant instant = Instant.parse(dateString);
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
        if (duration != null) {
            Pattern pattern = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?");
            Matcher matcher = pattern.matcher(duration);

            if (matcher.matches()) {
                int hours = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
                int minutes = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;

                return Duration.ofHours(hours).plusMinutes(minutes);
            }
        }
        return null;
    }
}
