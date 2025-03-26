/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analysis;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

import static java.util.function.Predicate.not;

public class AnalysisFinishedEvent {
  private final UUID analysisId;
  private final String configurationScopeId;
  private final Duration analysisDuration;
  private final Map<URI, SonarLanguage> languagePerFile;
  private final boolean succeededForAllFiles;
  private final List<RawIssue> issues;
  private final Set<String> reportedRuleKeys;
  private final Set<SonarLanguage> detectedLanguages;
  private final boolean shouldFetchServerIssues;

  public AnalysisFinishedEvent(UUID analysisId, String configurationScopeId, Duration analysisDuration, Map<URI, SonarLanguage> languagePerFile, boolean succeededForAllFiles,
    List<RawIssue> issues, boolean shouldFetchServerIssues) {
    this.analysisId = analysisId;
    this.configurationScopeId = configurationScopeId;
    this.analysisDuration = analysisDuration;
    this.languagePerFile = languagePerFile;
    this.succeededForAllFiles = succeededForAllFiles;
    this.issues = issues;
    this.reportedRuleKeys = issues.stream().map(RawIssue::getRuleKey).collect(Collectors.toSet());
    this.detectedLanguages = languagePerFile.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
    this.shouldFetchServerIssues = shouldFetchServerIssues;
  }

  public UUID getAnalysisId() {
    return analysisId;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public Duration getAnalysisDuration() {
    return analysisDuration;
  }

  public Map<URI, SonarLanguage> getLanguagePerFile() {
    return languagePerFile;
  }

  public boolean succeededForAllFiles() {
    return succeededForAllFiles;
  }

  public Set<String> getReportedRuleKeys() {
    return reportedRuleKeys;
  }

  public Set<SonarLanguage> getDetectedLanguages() {
    return detectedLanguages;
  }

  public List<RawIssue> getIssues() {
    return issues.stream().filter(not(RawIssue::isSecurityHotspot)).toList();
  }

  public List<RawIssue> getHotspots() {
    return issues.stream().filter(RawIssue::isSecurityHotspot).toList();
  }

  public boolean shouldFetchServerIssues() {
    return shouldFetchServerIssues;
  }
}
