/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.common.analysis;

import java.util.List;
import java.util.Optional;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

public final class DefaultClientIssue implements Issue {
  private final IssueSeverity severity;
  private final RuleType type;
  private final String ruleKey;
  private final String primaryMessage;
  private final ClientInputFile clientInputFile;
  private final List<Flow> flows;
  private final List<QuickFix> quickFixes;
  private final org.sonarsource.sonarlint.core.commons.TextRange textRange;
  private final Optional<String> ruleDescriptionContextKey;
  private final Optional<VulnerabilityProbability> vulnerabilityProbability;

  public DefaultClientIssue(org.sonarsource.sonarlint.core.analysis.api.Issue i, SonarLintRuleDefinition sonarLintRuleDefinition) {
    this.textRange = i.getTextRange() != null ? i.getTextRange() : null;
    this.primaryMessage = i.getMessage();
    this.clientInputFile = i.getInputFile();
    this.flows = i.flows();
    this.quickFixes = i.quickFixes();
    this.ruleDescriptionContextKey = i.getRuleDescriptionContextKey();
    this.severity = sonarLintRuleDefinition.getDefaultSeverity();
    this.type = sonarLintRuleDefinition.getType();
    this.ruleKey = sonarLintRuleDefinition.getKey();
    this.vulnerabilityProbability = sonarLintRuleDefinition.getVulnerabilityProbability();
  }

  public DefaultClientIssue(org.sonarsource.sonarlint.core.analysis.api.Issue i, IssueSeverity severity,
    RuleType type, Optional<VulnerabilityProbability> vulnerabilityProbability) {
    this.textRange = i.getTextRange() != null ? i.getTextRange() : null;
    this.primaryMessage = i.getMessage();
    this.clientInputFile = i.getInputFile();
    this.flows = i.flows();
    this.quickFixes = i.quickFixes();
    this.severity = severity;
    this.type = type;
    this.ruleKey = i.getRuleKey();
    this.ruleDescriptionContextKey = i.getRuleDescriptionContextKey();
    this.vulnerabilityProbability = vulnerabilityProbability;
  }

  @Override
  public IssueSeverity getSeverity() {
    return severity;
  }

  @Override
  public RuleType getType() {
    return type;
  }

  @Override
  public String getRuleKey() {
    return ruleKey;
  }

  @Override
  public String getMessage() {
    return primaryMessage;
  }

  @SuppressWarnings("unchecked")
  @CheckForNull
  @Override
  public ClientInputFile getInputFile() {
    return clientInputFile;
  }

  @Override
  public List<Flow> flows() {
    return flows;
  }

  @Override
  public List<QuickFix> quickFixes() {
    return quickFixes;
  }

  @Override
  public Optional<String> getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  @Override
  public Optional<VulnerabilityProbability> getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  @CheckForNull
  @Override
  public org.sonarsource.sonarlint.core.commons.TextRange getTextRange() {
    return textRange;
  }

  @Override
  public String toString() {
    var sb = new StringBuilder();
    sb.append("[");
    sb.append("rule=").append(ruleKey);
    sb.append(", severity=").append(severity);
    var startLine = getStartLine();
    if (startLine != null) {
      sb.append(", line=").append(startLine);
    }
    if (clientInputFile != null) {
      sb.append(", file=").append(clientInputFile.uri());
    }
    sb.append("]");
    return sb.toString();
  }
}
