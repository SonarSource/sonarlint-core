/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import static java.time.temporal.ChronoUnit.DAYS;

class TelemetryLocalStorage {
  @Deprecated
  private LocalDate installDate;
  private LocalDate lastUseDate;
  private LocalDateTime lastUploadDateTime;
  private OffsetDateTime installTime;
  private long numUseDays;
  private boolean enabled;
  private final Map<String, TelemetryAnalyzerPerformance> analyzers;
  private final Map<String, TelemetryNotificationsCounter> notificationsCountersByEventType;
  private int showHotspotRequestsCount;
  private int taintVulnerabilitiesInvestigatedLocallyCount;
  private int taintVulnerabilitiesInvestigatedRemotelyCount;
  private final Collection<String> explicitlyEnabledRules;
  private final Collection<String> explicitlyDisabledRules;
  private final Collection<String> reportedRules;

  TelemetryLocalStorage() {
    enabled = true;
    installTime = OffsetDateTime.now();
    analyzers = new LinkedHashMap<>();
    notificationsCountersByEventType = new LinkedHashMap<>();
    explicitlyEnabledRules = new HashSet<>();
    explicitlyDisabledRules = new HashSet<>();
    reportedRules = new HashSet<>();
  }

  public Collection<String> getExplicitlyEnabledRules() {
    return explicitlyEnabledRules;
  }

  public void addExplicitlyEnabledRule(String explicitlyEnabledRule) {
    this.explicitlyEnabledRules.add(explicitlyEnabledRule);
  }

  public Collection<String> getExplicitlyDisabledRules() {
    return explicitlyDisabledRules;
  }

  public void addExplicitlyDisabledRule(String explicitlyDisabledRule) {
    this.explicitlyDisabledRules.add(explicitlyDisabledRule);
  }

  public Collection<String> getReportedRules() {
    return reportedRules;
  }

  public void addReportedRule(String reportedRule) {
    this.reportedRules.add(reportedRule);
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

  public Map<String, TelemetryNotificationsCounter> notifications() {
    return notificationsCountersByEventType;
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

  void clearAfterPing() {
    this.analyzers.clear();
    this.notificationsCountersByEventType.clear();
    showHotspotRequestsCount = 0;
    taintVulnerabilitiesInvestigatedLocallyCount = 0;
    taintVulnerabilitiesInvestigatedRemotelyCount = 0;
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

  /**
   * Register that an analysis was performed.
   * This should be used when multiple files are analyzed.
   * @see #setUsedAnalysis(String, int)
   */
  void setUsedAnalysis() {
    markSonarLintAsUsedToday();
  }

  private void markSonarLintAsUsedToday() {
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
    markSonarLintAsUsedToday();

    TelemetryAnalyzerPerformance analyzer = analyzers.computeIfAbsent(language, x -> new TelemetryAnalyzerPerformance());
    analyzer.registerAnalysis(analysisTimeMs);
  }

  static boolean isOlder(@Nullable LocalDate first, @Nullable LocalDate second) {
    return first == null || (second != null && first.isBefore(second));
  }

  static boolean isOlder(@Nullable LocalDateTime first, @Nullable LocalDateTime second) {
    return first == null || (second != null && first.isBefore(second));
  }

  static TelemetryLocalStorage validateAndMigrate(TelemetryLocalStorage data) {
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
      data.analyzers.clear();
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

  public void incrementDevNotificationsCount(String eventType) {
    this.notificationsCountersByEventType.computeIfAbsent(eventType, k -> new TelemetryNotificationsCounter()).incrementDevNotificationsCount();
  }

  public void incrementDevNotificationsClicked(String eventType) {
    markSonarLintAsUsedToday();
    this.notificationsCountersByEventType.computeIfAbsent(eventType, k -> new TelemetryNotificationsCounter()).incrementDevNotificationsClicked();
  }

  public void incrementShowHotspotRequestCount() {
    markSonarLintAsUsedToday();
    showHotspotRequestsCount++;
  }

  public int showHotspotRequestsCount() {
    return showHotspotRequestsCount;
  }

  public void incrementTaintVulnerabilitiesInvestigatedLocallyCount() {
    markSonarLintAsUsedToday();
    taintVulnerabilitiesInvestigatedLocallyCount++;
  }

  public int taintVulnerabilitiesInvestigatedLocallyCount() {
    return taintVulnerabilitiesInvestigatedLocallyCount;
  }

  public void incrementTaintVulnerabilitiesInvestigatedRemotelyCount() {
    markSonarLintAsUsedToday();
    taintVulnerabilitiesInvestigatedRemotelyCount++;
  }

  public int taintVulnerabilitiesInvestigatedRemotelyCount() {
    return taintVulnerabilitiesInvestigatedRemotelyCount;
  }
}
