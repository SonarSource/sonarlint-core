/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.util.Set;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public class AnalysisFinishedEvent {
  private final String configurationScopeId;
  private final long analysisDuration;
  private final Set<SonarLanguage> analyzedLanguages;
  private final boolean succeededForAllFiles;
  private final Set<String> reportedRuleKeys;

  public AnalysisFinishedEvent(String configurationScopeId, long analysisDuration, Set<SonarLanguage> analyzedLanguages, boolean succeededForAllFiles,
    Set<String> reportedRuleKeys) {
    this.configurationScopeId = configurationScopeId;
    this.analysisDuration = analysisDuration;
    this.analyzedLanguages = analyzedLanguages;
    this.succeededForAllFiles = succeededForAllFiles;
    this.reportedRuleKeys = reportedRuleKeys;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public long getAnalysisDuration() {
    return analysisDuration;
  }

  public Set<SonarLanguage> getAnalyzedLanguages() {
    return analyzedLanguages;
  }

  public boolean succeededForAllFiles() {
    return succeededForAllFiles;
  }

  public Set<String> getReportedRuleKeys() {
    return reportedRuleKeys;
  }
}
