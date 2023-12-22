/*
 * SonarLint Core - Java Client Legacy
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
package org.sonarsource.sonarlint.core.client.legacy.analysis;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static java.util.stream.Collectors.toMap;

public final class RawIssue {
  private final IssueSeverity severity;
  private final RuleType type;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final Map<SoftwareQuality, ImpactSeverity> impacts;
  private final String ruleKey;
  private final String primaryMessage;
  private final ClientInputFile clientInputFile;
  private final List<Flow> flows;
  private final List<QuickFix> quickFixes;
  private final TextRangeDto textRange;
  private final Optional<String> ruleDescriptionContextKey;
  private final Optional<VulnerabilityProbability> vulnerabilityProbability;

  public RawIssue(org.sonarsource.sonarlint.core.analysis.api.Issue i, GetRuleDetailsResponse sonarLintRuleDefinition) {
    var range = i.getTextRange();
    this.textRange = range != null ? adapt(range) : null;
    this.primaryMessage = i.getMessage();
    this.clientInputFile = i.getInputFile();
    this.flows = i.flows();
    this.quickFixes = i.quickFixes();
    this.ruleDescriptionContextKey = i.getRuleDescriptionContextKey();
    this.severity = sonarLintRuleDefinition.getSeverity();
    this.type = sonarLintRuleDefinition.getType();
    this.cleanCodeAttribute = sonarLintRuleDefinition.getCleanCodeAttribute();
    this.impacts = new EnumMap<>(SoftwareQuality.class);
    this.impacts.putAll(sonarLintRuleDefinition.getDefaultImpacts().stream().collect(toMap(ImpactDto::getSoftwareQuality, ImpactDto::getImpactSeverity)));
    this.impacts
      .putAll(i.getOverriddenImpacts().entrySet().stream().map(entry -> Map.entry(SoftwareQuality.valueOf(entry.getKey().name()), ImpactSeverity.valueOf(entry.getValue().name())))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    this.ruleKey = i.getRuleKey();
    this.vulnerabilityProbability = Optional.ofNullable(sonarLintRuleDefinition.getVulnerabilityProbability());
  }

  private static TextRangeDto adapt(TextRange textRange) {
    return new TextRangeDto(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public RuleType getType() {
    return type;
  }

  public Optional<CleanCodeAttribute> getCleanCodeAttribute() {
    return Optional.ofNullable(cleanCodeAttribute);
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return impacts;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getMessage() {
    return primaryMessage;
  }

  @CheckForNull
  public ClientInputFile getInputFile() {
    return clientInputFile;
  }

  public List<Flow> getFlows() {
    return flows;
  }

  public List<QuickFix> quickFixes() {
    return quickFixes;
  }

  public Optional<String> getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  public Optional<VulnerabilityProbability> getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  @CheckForNull
  public TextRangeDto getTextRange() {
    return textRange;
  }

  @Override
  public String toString() {
    var sb = new StringBuilder();
    sb.append("[");
    sb.append("rule=").append(ruleKey);
    sb.append(", severity=").append(severity);
    if (textRange != null) {
      sb.append(", range=").append(toString(textRange));
    }
    if (clientInputFile != null) {
      sb.append(", file=").append(clientInputFile.uri());
    }
    sb.append("]");
    return sb.toString();
  }

  private static String toString(TextRangeDto textRange) {
    return "{ startLine=" + textRange.getStartLine() + ", startOffset=" + textRange.getStartLineOffset() + ", endLine=" + textRange.getEndLine() + ", endOffset="
      + textRange.getEndLineOffset() + " }";
  }
}
