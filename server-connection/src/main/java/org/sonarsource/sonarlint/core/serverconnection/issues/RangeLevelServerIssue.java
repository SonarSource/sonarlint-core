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
import javax.annotation.Nullable;

/**
 * Issues with precise location (from api/issues/pull, SQ >= 9.5)
 */
public class RangeLevelServerIssue extends ServerIssue<RangeLevelServerIssue> {
  private TextRange textRange;
  private String rangeHash;

  public RangeLevelServerIssue(String key, boolean resolved, String ruleKey, String message, String rangeHash, String filePath, Instant creationDate, @Nullable String userSeverity,
    String type,
    TextRange textRange) {
    super(key, resolved, ruleKey, message, filePath, creationDate, userSeverity, type);
    this.rangeHash = rangeHash;
    this.textRange = textRange;
  }

  public String getRangeHash() {
    return rangeHash;
  }

  public TextRange getTextRange() {
    return textRange;
  }

  public RangeLevelServerIssue setRangeHash(String rangeHash) {
    this.rangeHash = rangeHash;
    return this;
  }

  public RangeLevelServerIssue setTextRange(TextRange textRange) {
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
