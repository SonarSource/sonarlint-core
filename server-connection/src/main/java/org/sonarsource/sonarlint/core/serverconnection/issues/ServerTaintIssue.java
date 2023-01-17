/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.issues;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;

public class ServerTaintIssue {
  private String key;
  private boolean resolved;
  private String ruleKey;
  private String message;
  private String filePath;
  private Instant creationDate;
  private IssueSeverity severity;
  private RuleType type;
  private List<Flow> flows = new ArrayList<>();
  private TextRangeWithHash textRange;
  @Nullable
  private final String ruleDescriptionContextKey;

  public ServerTaintIssue(String key, boolean resolved, String ruleKey, String message, String filePath, Instant creationDate, IssueSeverity severity, RuleType type,
    @Nullable TextRangeWithHash textRange, @Nullable String ruleDescriptionContextKey) {
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
  }

  public String getKey() {
    return key;
  }

  public boolean isResolved() {
    return resolved;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getMessage() {
    return message;
  }

  public String getFilePath() {
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

  public ServerTaintIssue setFilePath(String filePath) {
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
    private final String filePath;
    private final TextRangeWithHash textRange;

    public ServerIssueLocation(@Nullable String filePath, @Nullable TextRangeWithHash textRange, @Nullable String message) {
      this.textRange = textRange;
      this.filePath = filePath;
      this.message = message;
    }

    @CheckForNull
    public String getFilePath() {
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
