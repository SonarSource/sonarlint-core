/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.analysis;

import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

/**
 * @deprecated since 10.2, replaced by {@link org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto} and {@link org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto}.
 * See {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFilesAndTrack(AnalyzeFilesAndTrackParams)}
 */
@Deprecated(since = "10.2")
public class RawIssueDto {
  private final IssueSeverity severity;
  private final RuleType type;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final Map<SoftwareQuality, ImpactSeverity> impacts;
  private final String ruleKey;
  private final String primaryMessage;
  private final URI fileUri;
  private final List<RawIssueFlowDto> flows;
  private final List<QuickFixDto> quickFixes;
  private final TextRangeDto textRange;
  @Nullable
  private final String ruleDescriptionContextKey;
  @Nullable
  private final VulnerabilityProbability vulnerabilityProbability;

  public RawIssueDto(IssueSeverity severity, RuleType type, CleanCodeAttribute cleanCodeAttribute, Map<SoftwareQuality, ImpactSeverity> impacts, String ruleKey,
    String primaryMessage, @Nullable URI fileUri, List<RawIssueFlowDto> flows, List<QuickFixDto> quickFixes, @Nullable TextRangeDto textRange,
    @Nullable String ruleDescriptionContextKey, @Nullable VulnerabilityProbability vulnerabilityProbability) {
    this.severity = severity;
    this.type = type;
    this.cleanCodeAttribute = cleanCodeAttribute;
    this.impacts = impacts;
    this.ruleKey = ruleKey;
    this.primaryMessage = primaryMessage;
    this.fileUri = fileUri;
    this.flows = flows;
    this.quickFixes = quickFixes;
    this.textRange = textRange;
    this.ruleDescriptionContextKey = ruleDescriptionContextKey;
    this.vulnerabilityProbability = vulnerabilityProbability;
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public RuleType getType() {
    return type;
  }

  public CleanCodeAttribute getCleanCodeAttribute() {
    return cleanCodeAttribute;
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return impacts;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getPrimaryMessage() {
    return primaryMessage;
  }

  @CheckForNull
  public URI getFileUri() {
    return fileUri;
  }

  public List<RawIssueFlowDto> getFlows() {
    return flows;
  }

  public List<QuickFixDto> getQuickFixes() {
    return quickFixes;
  }

  @CheckForNull
  public TextRangeDto getTextRange() {
    return textRange;
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  @CheckForNull
  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }
}
