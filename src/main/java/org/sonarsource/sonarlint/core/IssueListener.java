/*
 * SonarLint Core Library
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core;

import java.nio.file.Path;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public interface IssueListener {
  void handle(Issue issue);

  class Issue {
    private String ruleKey;
    private String ruleName;
    private String message;
    private String severity;
    private Path filePath;
    private Integer startLine;
    private Integer startLineOffset;
    private Integer endLine;
    private Integer endLineOffset;

    public String getSeverity() {
      return severity;
    }

    public Issue setSeverity(String severity) {
      this.severity = severity;
      return this;
    }

    public Integer getStartLine() {
      return startLine;
    }

    public Issue setStartLine(Integer startLine) {
      this.startLine = startLine;
      return this;
    }

    public Integer getStartLineOffset() {
      return startLineOffset;
    }

    public Issue setStartLineOffset(Integer startLineOffset) {
      this.startLineOffset = startLineOffset;
      return this;
    }

    public Integer getEndLine() {
      return endLine;
    }

    public Issue setEndLine(Integer endLine) {
      this.endLine = endLine;
      return this;
    }

    public Integer getEndLineOffset() {
      return endLineOffset;
    }

    public Issue setEndLineOffset(Integer endLineOffset) {
      this.endLineOffset = endLineOffset;
      return this;
    }

    public String getMessage() {
      return message;
    }

    public Issue setMessage(String message) {
      this.message = message;
      return this;
    }

    public String getRuleKey() {
      return ruleKey;
    }

    public Issue setRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    public String getRuleName() {
      return ruleName;
    }

    public Issue setRuleName(String ruleName) {
      this.ruleName = ruleName;
      return this;
    }

    /**
     * @return null for global issues
     */
    @CheckForNull
    public Path getFilePath() {
      return filePath;
    }

    public Issue setFilePath(Path filePath) {
      this.filePath = filePath;
      return this;
    }

    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }
  }
}
