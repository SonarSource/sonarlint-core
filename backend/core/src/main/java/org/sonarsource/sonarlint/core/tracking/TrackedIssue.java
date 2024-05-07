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
package org.sonarsource.sonarlint.core.tracking;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.RuleType;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;

public class TrackedIssue {
  private final UUID id;
  private final String message;
  private final String ruleKey;
  private final TextRangeWithHash textRangeWithHash;
  private final LineWithHash lineWithHash;
  private final String serverKey;
  private final Instant introductionDate;
  private final boolean resolved;
  private final IssueSeverity severity;
  private final RuleType type;
  private final boolean isOnNewCode;
  private final Map<SoftwareQuality, ImpactSeverity> impacts;
  private final List<Flow> flows;
  private final List<QuickFix> quickFixes;
  private final VulnerabilityProbability vulnerabilityProbability;
  private final String ruleDescriptionContextKey;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final URI fileUri;

  public TrackedIssue(UUID id, String message, Instant introductionDate, boolean resolved, IssueSeverity overriddenSeverity,
    RuleType type, String ruleKey, boolean isOnNewCode, @Nullable TextRangeWithHash textRangeWithHash,
    @Nullable LineWithHash lineWithHash, @Nullable String serverKey, Map<SoftwareQuality, ImpactSeverity> impacts,
    List<Flow> flows, List<QuickFix> quickFixes, VulnerabilityProbability vulnerabilityProbability,
    @Nullable String ruleDescriptionContextKey, CleanCodeAttribute cleanCodeAttribute, @Nullable URI fileUri) {
    this.id = id;
    this.message = message;
    this.ruleKey = ruleKey;
    this.textRangeWithHash = textRangeWithHash;
    this.lineWithHash = lineWithHash;
    this.serverKey = serverKey;
    this.introductionDate = introductionDate;
    this.resolved = resolved;
    this.severity = overriddenSeverity;
    this.type = type;
    this.isOnNewCode = isOnNewCode;
    this.impacts = impacts;
    this.flows = flows;
    this.quickFixes = quickFixes;
    this.vulnerabilityProbability = vulnerabilityProbability;
    this.ruleDescriptionContextKey = ruleDescriptionContextKey;
    this.cleanCodeAttribute = cleanCodeAttribute;
    this.fileUri = fileUri;
  }

  public UUID getId() {
    return id;
  }

  @CheckForNull
  public String getServerKey() {
    return serverKey;
  }

  public Instant getIntroductionDate() {
    return introductionDate;
  }

  public boolean isResolved() {
    return resolved;
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public RuleType getType() {
    return type;
  }

  public boolean isOnNewCode() {
    return isOnNewCode;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  @CheckForNull
  public TextRangeWithHash getTextRangeWithHash() {
    return textRangeWithHash;
  }

  public String getMessage() {
    return message;
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return impacts;
  }

  public List<Flow> getFlows() {
    return flows;
  }

  public List<QuickFix> getQuickFixes() {
    return quickFixes;
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  public CleanCodeAttribute getCleanCodeAttribute() {
    return cleanCodeAttribute;
  }

  @CheckForNull
  public LineWithHash getLineWithHash() {
    return lineWithHash;
  }

  @CheckForNull
  public URI getFileUri() {
    return fileUri;
  }
}
