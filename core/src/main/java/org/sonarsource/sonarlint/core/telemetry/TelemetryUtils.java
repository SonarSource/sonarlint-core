/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.telemetry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import javax.annotation.Nullable;

class TelemetryUtils {

  private TelemetryUtils() {
    // utility class, forbidden constructor
  }

  /**
   * Check if "now" is a different day than the reference.
   *
   * @param date reference date
   * @return true if it's a different day than the reference
   */
  static boolean dayChanged(@Nullable LocalDate date) {
    return date == null || !date.equals(LocalDate.now());
  }

  /**
   * Check if "now" is a different day than the reference, and some hours have elapsed.
   *
   * @param dateTime reference date
   * @param hours minimum hours that must have elapsed
   * @return true if it's a different day than the reference and at least hours have elapsed
   */
  static boolean dayChanged(@Nullable LocalDateTime dateTime, long hours) {
    return dateTime == null ||
      (!LocalDate.now().equals(dateTime.toLocalDate())
        && (dateTime.until(LocalDateTime.now(), ChronoUnit.HOURS) >= hours));
  }
}
