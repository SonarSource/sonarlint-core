/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus;

import static java.time.temporal.ChronoUnit.DAYS;

public class TelemetryLocalStorage {
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
  private int showIssueRequestsCount;
  private int openHotspotInBrowserCount;
  private int taintVulnerabilitiesInvestigatedLocallyCount;
  private int taintVulnerabilitiesInvestigatedRemotelyCount;
  private int hotspotStatusChangedCount;
  private final Set<String> issueStatusChangedRuleKeys;
  private int issueStatusChangedCount;
  private final Set<String> raisedIssuesRules;
  private final Set<String> quickFixesApplied;
  private final Map<String, Integer> quickFixCountByRuleKey;
  private final Map<String, TelemetryHelpAndFeedbackCounter> helpAndFeedbackLinkClickedCount;
  private final Map<AnalysisReportingType, TelemetryAnalysisReportingCounter> analysisReportingCountersByType;
  private final Map<String, TelemetryFindingsFilteredCounter> findingsFilteredCountersByType;
  private final Map<String, TelemetryFixSuggestionReceivedCounter> fixSuggestionReceivedCounter;
  private final Map<String, List<TelemetryFixSuggestionResolvedStatus>> fixSuggestionResolved;
  private final Map<String, ToolCallCounter> calledToolsByName;
  private final Set<UUID> issuesUuidAiFixableSeen;
  private boolean isFocusOnNewCode;
  private int codeFocusChangedCount;
  private int manualAddedBindingsCount;
  private int importedAddedBindingsCount;
  private int autoAddedBindingsCount;
  private int exportedConnectedModeCount;
  private int suggestedRemoteBindingsCount;
  private long newIssuesFoundCount;
  private long issuesFixedCount;
  private int biggestNumberOfFilesInConfigScope;
  private long listingTimeForBiggestNumberConfigScopeFiles;
  private long longestListingTimeForConfigScopeFiles;
  private int numberOfFilesForLongestFilesListingTimeConfigScope;
  private int taintInvestigatedLocallyCount;
  private int taintInvestigatedRemotelyCount;
  private int hotspotInvestigatedLocallyCount;
  private int hotspotInvestigatedRemotelyCount;
  private int issueInvestigatedLocallyCount;
  private int dependencyRiskInvestigatedRemotelyCount;
  private int dependencyRiskInvestigatedLocallyCount;

  TelemetryLocalStorage() {
    enabled = true;
    installTime = OffsetDateTime.now();
    analyzers = new LinkedHashMap<>();
    notificationsCountersByEventType = new LinkedHashMap<>();
    issueStatusChangedRuleKeys = new HashSet<>();
    raisedIssuesRules = new HashSet<>();
    quickFixesApplied = new HashSet<>();
    quickFixCountByRuleKey = new LinkedHashMap<>();
    helpAndFeedbackLinkClickedCount = new LinkedHashMap<>();
    analysisReportingCountersByType = new LinkedHashMap<>();
    findingsFilteredCountersByType = new LinkedHashMap<>();
    fixSuggestionReceivedCounter = new LinkedHashMap<>();
    fixSuggestionResolved = new LinkedHashMap<>();
    issuesUuidAiFixableSeen = new HashSet<>();
    calledToolsByName = new HashMap<>();
  }

  public Collection<String> getRaisedIssuesRules() {
    return raisedIssuesRules;
  }

  public void addReportedRules(Set<String> reportedRuleKeys) {
    this.raisedIssuesRules.addAll(reportedRuleKeys);
  }

  public Collection<String> getQuickFixesApplied() {
    return quickFixesApplied;
  }

  public void addQuickFixAppliedForRule(String ruleKey) {
    markSonarLintAsUsedToday();
    this.quickFixesApplied.add(ruleKey);
    var currentCountForKey = this.quickFixCountByRuleKey.getOrDefault(ruleKey, 0);
    this.quickFixCountByRuleKey.put(ruleKey, currentCountForKey + 1);
  }

  public Map<String, Integer> getQuickFixCountByRuleKey() {
    return quickFixCountByRuleKey;
  }

  @Deprecated
  void setInstallDate(LocalDate date) {
    this.installDate = date;
  }

  @Deprecated
  public LocalDate installDate() {
    return installDate;
  }

  public OffsetDateTime installTime() {
    return installTime;
  }

  public void setInstallTime(OffsetDateTime installTime) {
    this.installTime = installTime;
  }

  void setLastUseDate(@Nullable LocalDate date) {
    this.lastUseDate = date;
  }

  @CheckForNull
  public LocalDate lastUseDate() {
    return lastUseDate;
  }

  public Map<String, TelemetryAnalyzerPerformance> analyzers() {
    return analyzers;
  }

  public Map<String, TelemetryNotificationsCounter> notifications() {
    return notificationsCountersByEventType;
  }

  public Map<String, TelemetryHelpAndFeedbackCounter> getHelpAndFeedbackLinkClickedCounter() {
    return helpAndFeedbackLinkClickedCount;
  }

  public Map<AnalysisReportingType, TelemetryAnalysisReportingCounter> getAnalysisReportingCountersByType() {
    return analysisReportingCountersByType;
  }

  public Map<String, TelemetryFindingsFilteredCounter> getFindingsFilteredCountersByType() {
    return findingsFilteredCountersByType;
  }

  public Map<String, TelemetryFixSuggestionReceivedCounter> getFixSuggestionReceivedCounter() {
    return fixSuggestionReceivedCounter;
  }

  public Map<String, List<TelemetryFixSuggestionResolvedStatus>> getFixSuggestionResolved() {
    return fixSuggestionResolved;
  }

  public int getCountIssuesWithPossibleAiFixFromIde() {
    return issuesUuidAiFixableSeen.size();
  }

  public boolean isFocusOnNewCode() {
    return isFocusOnNewCode;
  }

  public int getCodeFocusChangedCount() {
    return codeFocusChangedCount;
  }

  void setLastUploadTime() {
    setLastUploadTime(LocalDateTime.now());
  }

  void setLastUploadTime(@Nullable LocalDateTime dateTime) {
    this.lastUploadDateTime = dateTime;
  }

  @CheckForNull
  public LocalDateTime lastUploadTime() {
    return lastUploadDateTime;
  }

  void setNumUseDays(long numUseDays) {
    this.numUseDays = numUseDays;
  }

  void clearAfterPing() {
    analyzers.clear();
    notificationsCountersByEventType.clear();
    showHotspotRequestsCount = 0;
    showIssueRequestsCount = 0;
    openHotspotInBrowserCount = 0;
    taintVulnerabilitiesInvestigatedLocallyCount = 0;
    taintVulnerabilitiesInvestigatedRemotelyCount = 0;
    hotspotStatusChangedCount = 0;
    issueStatusChangedRuleKeys.clear();
    issueStatusChangedCount = 0;
    raisedIssuesRules.clear();
    quickFixesApplied.clear();
    quickFixCountByRuleKey.clear();
    helpAndFeedbackLinkClickedCount.clear();
    analysisReportingCountersByType.clear();
    findingsFilteredCountersByType.clear();
    fixSuggestionReceivedCounter.clear();
    fixSuggestionResolved.clear();
    issuesUuidAiFixableSeen.clear();
    codeFocusChangedCount = 0;
    manualAddedBindingsCount = 0;
    importedAddedBindingsCount = 0;
    autoAddedBindingsCount = 0;
    exportedConnectedModeCount = 0;
    suggestedRemoteBindingsCount = 0;
    newIssuesFoundCount = 0;
    issuesFixedCount = 0;
    biggestNumberOfFilesInConfigScope = 0;
    calledToolsByName.clear();
    dependencyRiskInvestigatedLocallyCount = 0;
    dependencyRiskInvestigatedRemotelyCount = 0;
  }

  public long numUseDays() {
    return numUseDays;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean enabled() {
    return enabled;
  }

  /**
   * Register that an analysis was performed.
   * This should be used when multiple files are analyzed.
   *
   * @see #setUsedAnalysis(String, int)
   */
  void setUsedAnalysis() {
    markSonarLintAsUsedToday();
  }

  private void markSonarLintAsUsedToday() {
    var now = LocalDate.now();
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

    var analyzer = analyzers.computeIfAbsent(language, x -> new TelemetryAnalyzerPerformance());
    analyzer.registerAnalysis(analysisTimeMs);
  }

  static boolean isOlder(@Nullable LocalDate first, @Nullable LocalDate second) {
    return first == null || (second != null && first.isBefore(second));
  }

  static boolean isOlder(@Nullable LocalDateTime first, @Nullable LocalDateTime second) {
    return first == null || (second != null && first.isBefore(second));
  }

  void validateAndMigrate() {
    var today = LocalDate.now();

    // migrate deprecated installDate
    if (installDate != null && (installTime == null || installTime.toLocalDate().isAfter(installDate))) {
      setInstallTime(installDate.atTime(OffsetTime.now()));
    }

    // fix install time if necessary
    if (installTime == null || installTime.isAfter(OffsetDateTime.now())) {
      setInstallTime(OffsetDateTime.now());
    }

    // calculate use days
    if (lastUseDate == null) {
      numUseDays = 0;
      analyzers.clear();
      return;
    }

    if (lastUseDate.isBefore(installTime.toLocalDate())) {
      lastUseDate = installTime.toLocalDate();
    } else if (lastUseDate.isAfter(today)) {
      lastUseDate = today;
    }

    var maxUseDays = installTime.toLocalDate().until(lastUseDate, DAYS) + 1;
    if (numUseDays() > maxUseDays) {
      numUseDays = maxUseDays;
    }
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

  public void incrementShowIssueRequestCount() {
    markSonarLintAsUsedToday();
    showIssueRequestsCount++;
  }

  public void fixSuggestionReceived(String suggestionId, AiSuggestionSource aiSuggestionSource, int snippetsCount, boolean wasGeneratedFromIde) {
    markSonarLintAsUsedToday();
    this.fixSuggestionReceivedCounter.computeIfAbsent(suggestionId, k -> new TelemetryFixSuggestionReceivedCounter(aiSuggestionSource, snippetsCount, wasGeneratedFromIde));
  }

  public void fixSuggestionResolved(String suggestionId, FixSuggestionStatus status, @Nullable Integer snippetIndex) {
    markSonarLintAsUsedToday();
    var fixSuggestionSnippets = this.fixSuggestionResolved.computeIfAbsent(suggestionId, k -> new ArrayList<>());
    var existingSnippetStatus = fixSuggestionSnippets.stream()
      .filter(s -> {
        var previousIndex = s.getFixSuggestionResolvedSnippetIndex();
        return (snippetIndex == null && previousIndex == null) ||
          (previousIndex != null && previousIndex.equals(snippetIndex));
      })
      .findFirst();
    // if we already had a status for this snippet, we should replace it
    existingSnippetStatus.ifPresentOrElse(telemetryFixSuggestionResolvedStatus -> telemetryFixSuggestionResolvedStatus.setFixSuggestionResolvedStatus(status),
      () -> fixSuggestionSnippets.add(new TelemetryFixSuggestionResolvedStatus(status, snippetIndex)));
  }

  public void addIssuesWithPossibleAiFixFromIde(Set<UUID> issues) {
    markSonarLintAsUsedToday();
    issuesUuidAiFixableSeen.addAll(issues);
  }

  public int getShowIssueRequestsCount() {
    return showIssueRequestsCount;
  }

  public void incrementOpenHotspotInBrowserCount() {
    markSonarLintAsUsedToday();
    openHotspotInBrowserCount++;
  }

  public int openHotspotInBrowserCount() {
    return openHotspotInBrowserCount;
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

  public void helpAndFeedbackLinkClicked(String itemId) {
    this.helpAndFeedbackLinkClickedCount.computeIfAbsent(itemId, k -> new TelemetryHelpAndFeedbackCounter()).incrementHelpAndFeedbackLinkClickedCount();
  }

  public void analysisReportingTriggered(AnalysisReportingType analysisType) {
    this.analysisReportingCountersByType.computeIfAbsent(analysisType, k -> new TelemetryAnalysisReportingCounter()).incrementAnalysisReportingCount();
  }

  public void findingsFiltered(String filterType) {
    markSonarLintAsUsedToday();
    this.findingsFilteredCountersByType.computeIfAbsent(filterType, k -> new TelemetryFindingsFilteredCounter()).incrementFindingsFilteredCount();
  }

  public void incrementHotspotStatusChangedCount() {
    markSonarLintAsUsedToday();
    hotspotStatusChangedCount++;
  }

  public int hotspotStatusChangedCount() {
    return hotspotStatusChangedCount;
  }

  public void addIssueStatusChanged(String ruleKey) {
    markSonarLintAsUsedToday();
    issueStatusChangedRuleKeys.add(ruleKey);
    issueStatusChangedCount++;
  }

  public Set<String> issueStatusChangedRuleKeys() {
    return issueStatusChangedRuleKeys;
  }

  public int issueStatusChangedCount() {
    return issueStatusChangedCount;
  }

  public void setInitialNewCodeFocus(boolean focusOnNewCode) {
    markSonarLintAsUsedToday();
    this.isFocusOnNewCode = focusOnNewCode;
  }

  public void incrementNewCodeFocusChange() {
    markSonarLintAsUsedToday();
    this.isFocusOnNewCode = !this.isFocusOnNewCode;
    codeFocusChangedCount++;
  }

  public void incrementManualAddedBindingsCount() {
    markSonarLintAsUsedToday();
    manualAddedBindingsCount++;
  }

  public int getManualAddedBindingsCount() {
    return manualAddedBindingsCount;
  }

  public void incrementImportedAddedBindingsCount() {
    markSonarLintAsUsedToday();
    importedAddedBindingsCount++;
  }

  public int getImportedAddedBindingsCount() {
    return importedAddedBindingsCount;
  }

  public void incrementAutoAddedBindingsCount() {
    markSonarLintAsUsedToday();
    autoAddedBindingsCount++;
  }

  public int getAutoAddedBindingsCount() {
    return autoAddedBindingsCount;
  }

  public void incrementExportedConnectedModeCount() {
    markSonarLintAsUsedToday();
    exportedConnectedModeCount++;
  }

  public void incrementSuggestedRemoteBindingsCount() {
    suggestedRemoteBindingsCount++;
  }

  public int getExportedConnectedModeCount() {
    return exportedConnectedModeCount;
  }

  public int getSuggestedRemoteBindingsCount() {
    return suggestedRemoteBindingsCount;
  }

  public void addNewlyFoundIssues(long newIssues) {
    markSonarLintAsUsedToday();
    newIssuesFoundCount += newIssues;
  }

  public long getNewIssuesFoundCount() {
    return newIssuesFoundCount;
  }

  public void addFixedIssues(long fixedIssues) {
    markSonarLintAsUsedToday();
    issuesFixedCount += fixedIssues;
  }

  public long getIssuesFixedCount() {
    return issuesFixedCount;
  }

  public void incrementToolCalledCount(String toolName, boolean succeeded) {
    markSonarLintAsUsedToday();
    calledToolsByName.computeIfAbsent(toolName, k -> new ToolCallCounter()).incrementCount(succeeded);
  }

  public Map<String, ToolCallCounter> getCalledToolsByName() {
    return calledToolsByName;
  }

  public void updateListFilesPerformance(int size, long timeMs) {
    if (size > biggestNumberOfFilesInConfigScope) {
      biggestNumberOfFilesInConfigScope = size;
      listingTimeForBiggestNumberConfigScopeFiles = timeMs;
    }
    if (timeMs > longestListingTimeForConfigScopeFiles) {
      longestListingTimeForConfigScopeFiles = timeMs;
      numberOfFilesForLongestFilesListingTimeConfigScope = size;
    }
  }

  public int getBiggestNumberOfFilesInConfigScope() {
    return biggestNumberOfFilesInConfigScope;
  }

  public long getListingTimeForBiggestNumberConfigScopeFiles() {
    return listingTimeForBiggestNumberConfigScopeFiles;
  }

  public int getNumberOfFilesForLongestFilesListingTimeConfigScope() {
    return numberOfFilesForLongestFilesListingTimeConfigScope;
  }

  public long getLongestListingTimeForConfigScopeFiles() {
    return longestListingTimeForConfigScopeFiles;
  }

  public void incrementHotspotInvestigatedLocallyCount() {
    markSonarLintAsUsedToday();
    hotspotInvestigatedLocallyCount++;
  }

  public void incrementHotspotInvestigatedRemotelyCount() {
    markSonarLintAsUsedToday();
    hotspotInvestigatedRemotelyCount++;
  }

  public void incrementTaintInvestigatedLocallyCount() {
    markSonarLintAsUsedToday();
    taintInvestigatedLocallyCount++;
  }

  public void incrementTaintInvestigatedRemotelyCount() {
    markSonarLintAsUsedToday();
    taintInvestigatedRemotelyCount++;
  }

  public void incrementIssueInvestigatedLocallyCount() {
    markSonarLintAsUsedToday();
    issueInvestigatedLocallyCount++;
  }

  public void incrementDependencyRiskInvestigatedRemotelyCount() {
    markSonarLintAsUsedToday();
    dependencyRiskInvestigatedRemotelyCount++;
  }

  public void incrementDependencyRiskInvestigatedLocallyCount() {
    markSonarLintAsUsedToday();
    dependencyRiskInvestigatedLocallyCount++;
  }

  public int getHotspotInvestigatedRemotelyCount() {
    return hotspotInvestigatedRemotelyCount;
  }

  public int getHotspotInvestigatedLocallyCount() {
    return hotspotInvestigatedLocallyCount;
  }

  public int getTaintInvestigatedRemotelyCount() {
    return taintInvestigatedRemotelyCount;
  }

  public int getTaintInvestigatedLocallyCount() {
    return taintInvestigatedLocallyCount;
  }

  public int getIssueInvestigatedLocallyCount() {
    return issueInvestigatedLocallyCount;
  }

  public int getDependencyRiskInvestigatedRemotelyCount() {
    return dependencyRiskInvestigatedRemotelyCount;
  }

  public int getDependencyRiskInvestigatedLocallyCount() {
    return dependencyRiskInvestigatedLocallyCount;
  }
}
