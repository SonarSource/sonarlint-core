/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.client.api.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class DateUtils {

  private DateUtils() {
    // utility class, forbidden constructor
  }

  public static String toAge(long time) {
    LocalDateTime creation = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
    LocalDateTime now = LocalDateTime.now();

    long years = ChronoUnit.YEARS.between(creation, now);
    if (years > 0) {
      return pluralize(years, "year");
    }
    long months = ChronoUnit.MONTHS.between(creation, now);
    if (months > 0) {
      return pluralize(months, "month");
    }
    long days = ChronoUnit.DAYS.between(creation, now);
    if (days > 0) {
      return pluralize(days, "day");
    }
    long hours = ChronoUnit.HOURS.between(creation, now);
    if (hours > 0) {
      return pluralize(hours, "hour");
    }
    long minutes = ChronoUnit.MINUTES.between(creation, now);
    if (minutes > 0) {
      return pluralize(minutes, "minute");
    }

    return "few seconds ago";
  }

  private static String pluralize(long strictlyPositiveCount, String singular) {
    return pluralize(strictlyPositiveCount, singular, singular + "s");
  }

  private static String pluralize(long strictlyPositiveCount, String singular, String plural) {
    if (strictlyPositiveCount == 1) {
      return "1 " + singular + " ago";
    }
    return strictlyPositiveCount + " " + plural + " ago";
  }
}
