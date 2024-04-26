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

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.TextRange;

import javax.annotation.CheckForNull;
import java.util.List;
import java.util.Map;

public class RawIssue {

  private final Issue issue;
  private final RuleDetailsForAnalysis activeRule;


  public RawIssue(Issue issue, RuleDetailsForAnalysis activeRule) {
    this.issue = issue;
    this.activeRule = activeRule;
  }

  public IssueSeverity getSeverity() {
    return activeRule.getSeverity();
  }

  public RuleType getRuleType() {
    return activeRule.getType();
  }

  public CleanCodeAttribute getCleanCodeAttribute() {
    return activeRule.getCleanCodeAttribute();
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return activeRule.getImpacts();
  }

  public String getRuleKey() {
    return issue.getRuleKey();
  }

  public String getMessage() {
    return issue.getMessage();
  }

  public List<Flow> getFlows() {
    return issue.flows();
  }

  public List<QuickFix> getQuickFixes() {
    return issue.quickFixes();
  }

  @CheckForNull
  public TextRange getTextRange() {
    return issue.getTextRange();
  }

  @CheckForNull
  public Path getFilePath() {
    var inputFile = issue.getInputFile();
    return inputFile != null ? Path.of(inputFile.uri()) : null;
  }

  @CheckForNull
  public ClientInputFile getClientInputFile() {
    return issue.getInputFile();
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return activeRule.getVulnerabilityProbability();
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return issue.getRuleDescriptionContextKey().orElse(null);
  }

}
