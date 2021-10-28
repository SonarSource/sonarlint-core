/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.issue;

import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.TextRange;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.container.module.ActiveRuleAdapter;

public final class DefaultClientIssue implements org.sonarsource.sonarlint.core.analysis.api.Issue {
  private final String severity;
  private final String type;
  private final ActiveRuleAdapter activeRule;
  private final String primaryMessage;
  private final ClientInputFile clientInputFile;
  private final List<Flow> flows;
  private final List<QuickFix> quickFixes;
  private final org.sonarsource.sonarlint.core.analysis.api.TextRange textRange;

  public DefaultClientIssue(String severity, String type, ActiveRuleAdapter activeRule, String primaryMessage, @Nullable TextRange textRange,
    @Nullable ClientInputFile clientInputFile, List<Flow> flows, List<QuickFix> quickFixes) {
    this.textRange = textRange != null ? TextRangeUtils.convert(textRange) : null;
    this.severity = severity;
    this.type = type;
    this.activeRule = activeRule;
    this.primaryMessage = primaryMessage;
    this.clientInputFile = clientInputFile;
    this.flows = flows;
    this.quickFixes = quickFixes;
  }

  @Override
  public String getSeverity() {
    return severity;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getRuleName() {
    return activeRule.getRuleName();
  }

  @Override
  public String getRuleKey() {
    return activeRule.ruleKey().toString();
  }

  @Override
  public String getMessage() {
    return primaryMessage;
  }

  @CheckForNull
  @Override
  public ClientInputFile getInputFile() {
    return clientInputFile;
  }

  @Override
  public List<Flow> flows() {
    return flows;
  }

  @Override
  public List<QuickFix> quickFixes() {
    return quickFixes;
  }

  @CheckForNull
  @Override
  public org.sonarsource.sonarlint.core.analysis.api.TextRange getTextRange() {
    return textRange;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append("rule=").append(activeRule.ruleKey());
    sb.append(", severity=").append(severity);
    Integer startLine = getStartLine();
    if (startLine != null) {
      sb.append(", line=").append(startLine);
    }
    if (clientInputFile != null) {
      sb.append(", file=").append(clientInputFile.uri());
    }
    sb.append("]");
    return sb.toString();
  }
}
