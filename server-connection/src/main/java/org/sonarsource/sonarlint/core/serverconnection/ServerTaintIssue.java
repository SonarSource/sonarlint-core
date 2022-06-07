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
package org.sonarsource.sonarlint.core.serverconnection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ServerTaintIssue {
  private String key;
  private final boolean resolved;
  private String ruleKey;
  private String message;
  private String lineHash;
  private String filePath;
  private Instant creationDate;
  private String severity;
  private String type;
  private List<Flow> flows = new ArrayList<>();
  private TextRange textRange;
  private String codeSnippet;

  public ServerTaintIssue(String key, boolean resolved, String ruleKey, String message, String lineHash, String filePath, Instant creationDate, String severity, String type,
    @Nullable TextRange textRange) {
    this.key = key;
    this.resolved = resolved;
    this.ruleKey = ruleKey;
    this.message = message;
    this.lineHash = lineHash;
    this.filePath = filePath;
    this.creationDate = creationDate;
    this.severity = severity;
    this.type = type;
    this.textRange = textRange;
  }

  public String key() {
    return key;
  }

  public boolean resolved() {
    return resolved;
  }

  public String ruleKey() {
    return ruleKey;
  }

  public String getMessage() {
    return message;
  }

  public String lineHash() {
    return lineHash;
  }

  public String getFilePath() {
    return filePath;
  }

  public Instant creationDate() {
    return creationDate;
  }

  public String severity() {
    return severity;
  }

  public String type() {
    return type;
  }

  public TextRange getTextRange() {
    return textRange;
  }

  public List<Flow> getFlows() {
    return flows;
  }

  @CheckForNull
  public String getCodeSnippet() {
    return codeSnippet;
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

  public ServerTaintIssue setLineHash(String lineHash) {
    this.lineHash = lineHash;
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

  public ServerTaintIssue setSeverity(String severity) {
    this.severity = severity;
    return this;
  }

  public ServerTaintIssue setType(String type) {
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

  public ServerTaintIssue setCodeSnippet(@Nullable String codeSnippet) {
    this.codeSnippet = codeSnippet;
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
    private final String codeSnippet;
    private final TextRange textRange;

    public ServerIssueLocation(@Nullable String filePath, @Nullable TextRange textRange, @Nullable String message, @Nullable String codeSnippet) {
      this.textRange = textRange;
      this.filePath = filePath;
      this.message = message;
      this.codeSnippet = codeSnippet;
    }

    @CheckForNull
    public String getFilePath() {
      return filePath;
    }

    public String getMessage() {
      return message;
    }

    @CheckForNull
    public String getCodeSnippet() {
      return codeSnippet;
    }

    @CheckForNull
    public TextRange getTextRange() {
      return textRange;
    }
  }
}
