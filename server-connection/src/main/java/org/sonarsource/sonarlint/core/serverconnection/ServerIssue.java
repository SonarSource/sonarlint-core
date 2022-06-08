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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ServerIssue {
  private String key;
  private final boolean resolved;
  private String ruleKey;
  private String message;
  private String filePath;
  private Instant creationDate;
  private String severity;
  private String type;
  // Issues from batch/issues (SQ < 9.5 and SC)
  private Integer line;
  private String lineHash;
  // Issues from api/issues/pull (SQ >= 9.5)
  private TextRange textRange;
  private String rangeHash;

  public ServerIssue(String key, boolean resolved, String ruleKey, String message, @Nullable String lineHash, String filePath, Instant creationDate, String severity, String type,
    @Nullable Integer line) {
    this.key = key;
    this.resolved = resolved;
    this.ruleKey = ruleKey;
    this.message = message;
    this.lineHash = lineHash;
    this.filePath = filePath;
    this.creationDate = creationDate;
    this.severity = severity;
    this.type = type;
    this.line = line;
  }

  public ServerIssue(String key, boolean resolved, String ruleKey, String message, @Nullable String rangeHash, String filePath, Instant creationDate, String severity, String type,
    @Nullable TextRange textRange) {
    this.key = key;
    this.resolved = resolved;
    this.ruleKey = ruleKey;
    this.message = message;
    this.rangeHash = rangeHash;
    this.filePath = filePath;
    this.creationDate = creationDate;
    this.severity = severity;
    this.type = type;
    this.textRange = textRange;
  }

  public String getKey() {
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

  @CheckForNull
  public String getLineHash() {
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

  @CheckForNull
  public Integer getLine() {
    return line;
  }

  @CheckForNull
  public String getRangeHash() {
    return rangeHash;
  }

  @CheckForNull
  public TextRange getTextRange() {
    return textRange;
  }

  public ServerIssue setKey(String key) {
    this.key = key;
    return this;
  }

  public ServerIssue setRuleKey(String ruleKey) {
    this.ruleKey = ruleKey;
    return this;
  }

  public ServerIssue setMessage(String message) {
    this.message = message;
    return this;
  }

  public ServerIssue setLineHash(@Nullable String lineHash) {
    this.lineHash = lineHash;
    return this;
  }

  public ServerIssue setFilePath(String filePath) {
    this.filePath = filePath;
    return this;
  }

  public ServerIssue setCreationDate(Instant creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public ServerIssue setSeverity(String severity) {
    this.severity = severity;
    return this;
  }

  public ServerIssue setType(String type) {
    this.type = type;
    return this;
  }

  public ServerIssue setLine(@Nullable Integer line) {
    this.line = line;
    return this;
  }

  public ServerIssue setRangeHash(String rangeHash) {
    this.rangeHash = rangeHash;
    return this;
  }

  public ServerIssue setTextRange(TextRange textRange) {
    this.textRange = textRange;
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

}
