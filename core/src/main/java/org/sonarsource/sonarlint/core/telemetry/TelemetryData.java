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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import static java.time.temporal.ChronoUnit.DAYS;

class TelemetryData {
  private LocalDate installDate;
  private LocalDate lastUseDate;
  private LocalDateTime lastUploadDateTime;
  private long numUseDays;
  private boolean enabled;
  private boolean usedConnectedMode;

  void setInstallDate(LocalDate date) {
    this.installDate = date;
  }

  LocalDate installDate() {
    return installDate;
  }

  void setLastUseDate(@Nullable LocalDate date) {
    this.lastUseDate = date;
  }

  @CheckForNull
  LocalDate lastUseDate() {
    return lastUseDate;
  }

  void setLastUploadTime() {
    setLastUploadTime(LocalDateTime.now());
  }

  void setLastUploadTime(@Nullable LocalDateTime dateTime) {
    this.lastUploadDateTime = dateTime;
  }

  @CheckForNull
  LocalDateTime lastUploadTime() {
    return lastUploadDateTime;
  }

  void setNumUseDays(long numUseDays) {
    this.numUseDays = numUseDays;
  }

  long numUseDays() {
    return numUseDays;
  }

  void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  boolean enabled() {
    return enabled;
  }

  public void setUsedConnectedMode(boolean value) {
    usedConnectedMode = value;
  }

  public boolean usedConnectedMode() {
    return usedConnectedMode;
  }

  public void usedAnalysis() {
    LocalDate now = LocalDate.now();
    if (lastUseDate == null || !lastUseDate.equals(now)) {
      numUseDays++;
    }
    lastUseDate = now;
  }

  /**
   * Merge from existing telemetry data to avoid overwriting values
   * that might be more up to date than the current instance.
   *
   * @param other existing telemetry data to merge from
   */
  public void mergeFrom(TelemetryData other) {
    if (other.usedConnectedMode) {
      usedConnectedMode = true;
    }

    if (isOlder(lastUseDate, other.lastUseDate)) {
      lastUseDate = other.lastUseDate;
    }

    if (other.numUseDays > numUseDays) {
      numUseDays = other.numUseDays;
      installDate = other.installDate;
    }

    if (isOlder(lastUploadDateTime, other.lastUploadDateTime)) {
      lastUploadDateTime = other.lastUploadDateTime;
    }
  }

  static boolean isOlder(@Nullable LocalDate first, @Nullable LocalDate second) {
    return first == null || second != null && first.isBefore(second);
  }

  static boolean isOlder(@Nullable LocalDateTime first, @Nullable LocalDateTime second) {
    return first == null || second != null && first.isBefore(second);
  }

  static TelemetryData validate(TelemetryData data) {
    LocalDate today = LocalDate.now();

    if (data.installDate() == null || data.installDate().isAfter(today)) {
      data.setInstallDate(today);
    }

    LocalDate lastUseDate = data.lastUseDate();
    if (lastUseDate == null) {
      data.setNumUseDays(0);
      return data;
    }

    if (lastUseDate.isBefore(data.installDate())) {
      data.setLastUseDate(data.installDate());
    } else if (lastUseDate.isAfter(today)) {
      data.setLastUseDate(today);
    }

    long maxUseDays = data.installDate().until(data.lastUseDate(), DAYS) + 1;
    if (data.numUseDays() > maxUseDays) {
      data.setNumUseDays(maxUseDays);
      data.setLastUseDate(data.lastUseDate());
    }

    return data;
  }
}
