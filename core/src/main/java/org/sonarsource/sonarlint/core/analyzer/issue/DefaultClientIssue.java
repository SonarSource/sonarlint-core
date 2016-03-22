/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analyzer.issue;

import javax.annotation.Nullable;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.Rule;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public final class DefaultClientIssue implements org.sonarsource.sonarlint.core.client.api.common.analysis.Issue {
  private final String severity;
  private final ActiveRule activeRule;
  private final String primaryMessage;
  private final TextRange textRange;
  private final ClientInputFile clientInputFile;
  private final Rule rule;

  public DefaultClientIssue(String severity, ActiveRule activeRule, Rule rule, String primaryMessage, @Nullable TextRange textRange, @Nullable ClientInputFile clientInputFile) {
    this.severity = severity;
    this.activeRule = activeRule;
    this.rule = rule;
    this.primaryMessage = primaryMessage;
    this.textRange = textRange;
    this.clientInputFile = clientInputFile;
  }

  @Override
  public Integer getStartLineOffset() {
    return textRange != null ? textRange.start().lineOffset() : null;
  }

  @Override
  public Integer getStartLine() {
    return textRange != null ? textRange.start().line() : null;
  }

  @Override
  public String getSeverity() {
    return severity;
  }

  @Override
  public String getRuleName() {
    return rule.name();
  }

  @Override
  public String getRuleKey() {
    return activeRule.ruleKey().toString();
  }

  @Override
  public String getMessage() {
    return primaryMessage;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ClientInputFile getInputFile() {
    return clientInputFile;
  }

  @Override
  public Integer getEndLineOffset() {
    return textRange != null ? textRange.end().lineOffset() : null;
  }

  @Override
  public Integer getEndLine() {
    return textRange != null ? textRange.end().line() : null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append("rule=").append(activeRule.ruleKey());
    sb.append(", severity=").append(severity);
    if (textRange != null) {
      sb.append(", line=").append(textRange.start().line());
    }
    if (clientInputFile != null) {
      sb.append(", file=").append(clientInputFile.getPath().toString());
    }
    sb.append("]");
    return sb.toString();
  }
}
