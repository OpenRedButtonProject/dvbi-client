/**
 * ORB Software. Copyright (c) 2026 Ocean Blue Software Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
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
