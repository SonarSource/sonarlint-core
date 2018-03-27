/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

class TelemetryData {
  @Deprecated
  private LocalDate installDate;
  private LocalDate lastUseDate;
  private LocalDateTime lastUploadDateTime;
  private OffsetDateTime installTime;
  private long numUseDays;
  private boolean enabled;
  private boolean usedConnectedMode;
  private Map<String, TelemetryAnalyzerPerformance> analyzers;

  TelemetryData() {
    enabled = true;
    installTime = OffsetDateTime.now();
    analyzers = new LinkedHashMap<>();
  }

  @Deprecated
  void setInstallDate(LocalDate date) {
    this.installDate = date;
  }

  @Deprecated
  LocalDate installDate() {
    return installDate;
  }

  OffsetDateTime installTime() {
    return installTime;
  }

  public void setInstallTime(OffsetDateTime installTime) {
    this.installTime = installTime;
  }

  void setLastUseDate(@Nullable LocalDate date) {
    this.lastUseDate = date;
  }

  @CheckForNull
  LocalDate lastUseDate() {
    return lastUseDate;
  }

  public Map<String, TelemetryAnalyzerPerformance> analyzers() {
    return analyzers;
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

  void clearAnalyzers() {
    this.analyzers = new LinkedHashMap<>();
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

  void setUsedConnectedMode(boolean value) {
    usedConnectedMode = value;
  }

  boolean usedConnectedMode() {
    return usedConnectedMode;
  }

  /**
   * Register that an analysis was performed.
   * This should be used when multiple files are analyzed.
   * @see #setUsedAnalysis(String, int)
   */
  void setUsedAnalysis() {
    LocalDate now = LocalDate.now();
    if (lastUseDate == null || !lastUseDate.equals(now)) {
      numUseDays++;
    }
    lastUseDate = now;
  }

  /**
   * Register the analysis of a single file, with information regarding language and duration of the analysis.
   */
  void setUsedAnalysis(String language, int analysisTimeMs) {
    setUsedAnalysis();

    TelemetryAnalyzerPerformance analyzer = analyzers.computeIfAbsent(language, x -> new TelemetryAnalyzerPerformance());
    analyzer.registerAnalysis(analysisTimeMs);
  }

  /**
   * Merge from existing telemetry data to avoid overwriting values
   * that might be more up to date than the current instance.
   *
   * @param other existing telemetry data to merge from
   */
  void mergeFrom(TelemetryData other) {
    if (other.usedConnectedMode) {
      usedConnectedMode = true;
    }

    if (isOlder(lastUseDate, other.lastUseDate)) {
      lastUseDate = other.lastUseDate;
    }

    if (other.numUseDays > numUseDays) {
      numUseDays = other.numUseDays;
      installTime = other.installTime;
    }

    if (isOlder(lastUploadDateTime, other.lastUploadDateTime)) {
      lastUploadDateTime = other.lastUploadDateTime;
    }
  }

  static boolean isOlder(@Nullable LocalDate first, @Nullable LocalDate second) {
    return first == null || (second != null && first.isBefore(second));
  }

  static boolean isOlder(@Nullable LocalDateTime first, @Nullable LocalDateTime second) {
    return first == null || (second != null && first.isBefore(second));
  }

  static TelemetryData validateAndMigrate(TelemetryData data) {
    LocalDate today = LocalDate.now();

    // migrate deprecated installDate
    if (data.installDate() != null && (data.installTime() == null || data.installTime().toLocalDate().isAfter(data.installDate()))) {
      data.setInstallTime(data.installDate.atTime(OffsetTime.now()));
    }

    // fix install time if necessary
    if (data.installTime() == null || data.installTime().isAfter(OffsetDateTime.now())) {
      data.setInstallTime(OffsetDateTime.now());
    }

    // calculate use days
    LocalDate lastUseDate = data.lastUseDate();
    if (lastUseDate == null) {
      data.setNumUseDays(0);
      data.analyzers = new LinkedHashMap<>();
      return data;
    }

    if (lastUseDate.isBefore(data.installTime().toLocalDate())) {
      data.setLastUseDate(data.installTime().toLocalDate());
    } else if (lastUseDate.isAfter(today)) {
      data.setLastUseDate(today);
    }

    long maxUseDays = data.installTime().toLocalDate().until(data.lastUseDate(), DAYS) + 1;
    if (data.numUseDays() > maxUseDays) {
      data.setNumUseDays(maxUseDays);
      data.setLastUseDate(data.lastUseDate());
    }

    return data;
  }
}
