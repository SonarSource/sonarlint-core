/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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

public class ServerTaintIssue {
  private String key;
  private final boolean resolved;
  private String ruleKey;
  private String message;
  private String filePath;
  private Instant creationDate;
  private IssueSeverity severity;
  private RuleType type;
  private List<Flow> flows = new ArrayList<>();
  private TextRange textRange;
  private String textRangeHash;

  public ServerTaintIssue(String key, boolean resolved, String ruleKey, String message, String filePath, Instant creationDate, IssueSeverity severity, RuleType type,
    @Nullable TextRange textRange, @Nullable String textRangeHash) {
    this.key = key;
    this.resolved = resolved;
    this.ruleKey = ruleKey;
    this.message = message;
    this.filePath = filePath;
    this.creationDate = creationDate;
    this.severity = severity;
    this.type = type;
    this.textRange = textRange;
    this.textRangeHash = textRangeHash;
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

  public TextRange getTextRange() {
    return textRange;
  }

  public List<Flow> getFlows() {
    return flows;
  }

  @CheckForNull
  public String getTextRangeHash() {
    return textRangeHash;
  }

  public ServerTaintIssue setKey(String key) {
    this.key = key;
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

  public ServerTaintIssue setTextRange(@Nullable TextRange textRange) {
    this.textRange = textRange;
    return this;
  }

  public ServerTaintIssue setFlows(List<Flow> flows) {
    this.flows = flows;
    return this;
  }

  public ServerTaintIssue setTextRangeHash(@Nullable String textRangeHash) {
    this.textRangeHash = textRangeHash;
    return this;
  }

  public static class TextRange {

    private final int startLine;
    private final int startLineOffset;
    private final int endLine;
    private final int endLineOffset;

    public TextRange(int startLine, int startLineOffset, int endLine, int endLineOffset) {
      this.startLine = startLine;
      this.startLineOffset = startLineOffset;
      this.endLine = endLine;
      this.endLineOffset = endLineOffset;
    }

    public int getStartLine() {
      return startLine;
    }

    public int getStartLineOffset() {
      return startLineOffset;
    }

    public int getEndLine() {
      return endLine;
    }

    public int getEndLineOffset() {
      return endLineOffset;
    }

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
    private final String textRangeHash;
    private final TextRange textRange;

    public ServerIssueLocation(@Nullable String filePath, @Nullable TextRange textRange, @Nullable String message, @Nullable String textRangeHash) {
      this.textRange = textRange;
      this.filePath = filePath;
      this.message = message;
      this.textRangeHash = textRangeHash;
    }

    @CheckForNull
    public String getFilePath() {
      return filePath;
    }

    public String getMessage() {
      return message;
    }

    @CheckForNull
    public String getTextRangeHash() {
      return textRangeHash;
    }

    @CheckForNull
    public TextRange getTextRange() {
      return textRange;
    }
  }
}
