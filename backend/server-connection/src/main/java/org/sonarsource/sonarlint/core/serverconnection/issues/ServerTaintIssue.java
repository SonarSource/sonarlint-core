/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.issues;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;

public class ServerTaintIssue implements ServerFinding {
  private final UUID id;
  private String key;
  private boolean resolved;
  private String ruleKey;
  private String message;
  private Path filePath;
  private Instant creationDate;
  private IssueSeverity severity;
  private RuleType type;
  private List<Flow> flows = new ArrayList<>();
  private TextRangeWithHash textRange;
  @Nullable
  private final String ruleDescriptionContextKey;
  @Nullable
  private final CleanCodeAttribute cleanCodeAttribute;
  private final Map<SoftwareQuality, ImpactSeverity> impacts;

  public ServerTaintIssue(UUID id, String key, boolean resolved, String ruleKey, String message, Path filePath, Instant creationDate, IssueSeverity severity, RuleType type,
                          @Nullable TextRangeWithHash textRange, @Nullable String ruleDescriptionContextKey, @Nullable CleanCodeAttribute cleanCodeAttribute,
                          Map<SoftwareQuality, ImpactSeverity> impacts) {
    this.id = id;
    this.key = key;
    this.resolved = resolved;
    this.ruleKey = ruleKey;
    this.message = message;
    this.filePath = filePath;
    this.creationDate = creationDate;
    this.severity = severity;
    this.type = type;
    this.textRange = textRange;
    this.ruleDescriptionContextKey = ruleDescriptionContextKey;
    this.cleanCodeAttribute = cleanCodeAttribute;
    this.impacts = impacts;
  }

  public UUID getId() {
    return id;
  }

  public String getSonarServerKey() {
    return key;
  }

  public boolean isResolved() {
    return resolved;
  }

  @Override
  public String getRuleKey() {
    return ruleKey;
  }

  public String getMessage() {
    return message;
  }

  public Path getFilePath() {
    return filePath;
  }

  public Instant getCreationDate() {
    return creationDate;
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public RuleType getType() {
    return type;
  }

  @CheckForNull
  public TextRangeWithHash getTextRange() {
    return textRange;
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  public List<Flow> getFlows() {
    return flows;
  }

  public Optional<CleanCodeAttribute> getCleanCodeAttribute() {
    return Optional.ofNullable(cleanCodeAttribute);
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return impacts;
  }

  public ServerTaintIssue setKey(String key) {
    this.key = key;
    return this;
  }

  public ServerTaintIssue setResolved(boolean resolved) {
    this.resolved = resolved;
    return this;
  }

  public ServerTaintIssue setRuleKey(String ruleKey) {
    this.ruleKey = ruleKey;
    return this;
  }

  public ServerTaintIssue setMessage(String message) {
    this.message = message;
    return this;
  }

  public ServerTaintIssue setFilePath(Path filePath) {
    this.filePath = filePath;
    return this;
  }

  public ServerTaintIssue setCreationDate(Instant creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public ServerTaintIssue setSeverity(IssueSeverity severity) {
    this.severity = severity;
    return this;
  }

  public ServerTaintIssue setType(RuleType type) {
    this.type = type;
    return this;
  }

  public ServerTaintIssue setTextRange(@Nullable TextRangeWithHash textRange) {
    this.textRange = textRange;
    return this;
  }

  public ServerTaintIssue setFlows(List<Flow> flows) {
    this.flows = flows;
    return this;
  }

  public static class Flow {
    private final List<ServerIssueLocation> locations;

    public Flow(List<ServerIssueLocation> locations) {
      this.locations = locations;
    }

    public List<ServerIssueLocation> locations() {
      return locations;
    }
  }

  public static class ServerIssueLocation {
    private final String message;
    private final Path filePath;
    private final TextRangeWithHash textRange;

    public ServerIssueLocation(@Nullable Path filePath, @Nullable TextRangeWithHash textRange, @Nullable String message) {
      this.textRange = textRange;
      this.filePath = filePath;
      this.message = message;
    }

    @CheckForNull
    public Path getFilePath() {
      return filePath;
    }

    public String getMessage() {
      return message;
    }

    @CheckForNull
    public TextRangeWithHash getTextRange() {
      return textRange;
    }
  }
}
