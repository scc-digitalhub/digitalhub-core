/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package it.smartcommunitylabdhub.commons.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.SneakyThrows;

/**
 * Utility class for handling date-related operations.
 */
public class DateUtils {

    private static final long MULTIPLIER = 1000;

    /**
     * Parse a timestamp string into a Date object.
     *
     * @param timestampStr   The timestamp string to parse.
     * @param isMilliseconds True if the timestamp is in milliseconds, false if in seconds.
     * @return The parsed Date object.
     */
    public static Date parseDateFromTimestamp(String timestampStr, boolean isMilliseconds) {
        long timestamp = Long.parseLong(timestampStr);
        return isMilliseconds ? new Date(timestamp) : new Date(timestamp * MULTIPLIER);
    }

    /**
     * Parse a date interval string into a DateInterval record.
     *
     * @param intervalStr    The date interval string in the format "startTimestamp,endTimestamp".
     * @param isMilliseconds True if timestamps are in milliseconds, false if in seconds.
     * @return The parsed DateInterval record.
     * @throws IllegalArgumentException if the date interval format is invalid.
     */
    public static DateInterval parseDateIntervalFromTimestamps(String intervalStr, boolean isMilliseconds)
        throws IllegalArgumentException {
        String[] timestampArray = intervalStr.split(",");

        if (timestampArray.length != 2) {
            throw new IllegalArgumentException("Invalid date interval format");
        }

        long startTimestamp = Long.parseLong(timestampArray[0]);
        long endTimestamp = Long.parseLong(timestampArray[1]);

        Date startDate = isMilliseconds ? new Date(startTimestamp) : new Date(startTimestamp * MULTIPLIER);
        Date endDate = isMilliseconds ? new Date(endTimestamp) : new Date(endTimestamp * MULTIPLIER);

        return new DateInterval(startDate, endDate);
    }

    /**
     * Parse a date string into a Date object using a specified format.
     *
     * @param dateStr The date string to parse.
     * @param format  The format of the date string.
     * @return The parsed Date object.
     * @throws ParseException if parsing fails.
     */
    @SneakyThrows
    public static Date parseDate(String dateStr, String format) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        return dateFormat.parse(dateStr);
    }

    /**
     * Parse a date interval string into a DateInterval record using a specified format.
     *
     * @param intervalStr The date interval string in the format "startDate,endDate".
     * @param format      The format of the date strings.
     * @return The parsed DateInterval record.
     * @throws IllegalArgumentException if the date interval format is invalid.
     * @throws ParseException           if parsing fails.
     */
    public static DateInterval parseDateInterval(String intervalStr, String format) throws ParseException {
        String[] dateArray = intervalStr.split(",");

        if (dateArray.length != 2) {
            throw new IllegalArgumentException("Invalid date interval format");
        }

        Date startDate = parseDate(dateArray[0], format);
        Date endDate = parseDate(dateArray[1], format);

        return new DateInterval(startDate, endDate);
    }

    /**
     * A record representing a date interval with start and end dates.
     */
    public record DateInterval(Date startDate, Date endDate) {}
}
