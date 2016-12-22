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

import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

import com.google.common.collect.Lists;

public final class DefaultClientIssue implements org.sonarsource.sonarlint.core.client.api.common.analysis.Issue {
  private final String severity;
  private final ActiveRule activeRule;
  private final String primaryMessage;
  private final TextRange textRange;
  private final ClientInputFile clientInputFile;
  private final Rule rule;
  private final List<Flow> flows;

  public DefaultClientIssue(String severity, ActiveRule activeRule, Rule rule, String primaryMessage, @Nullable TextRange textRange,
    @Nullable ClientInputFile clientInputFile, List<org.sonar.api.batch.sensor.issue.Issue.Flow> flows) {
    this.severity = severity;
    this.activeRule = activeRule;
    this.rule = rule;
    this.primaryMessage = primaryMessage;
    this.textRange = textRange;
    this.clientInputFile = clientInputFile;
    this.flows = Lists.transform(flows, f -> new DefaultFlow(f.locations()));
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

  @Override
  public Integer getStartLineOffset() {
    return textRange != null ? textRange.start().lineOffset() : null;
  }

  @Override
  public Integer getStartLine() {
    return textRange != null ? textRange.start().line() : null;
  }

  @Override
  public Integer getEndLineOffset() {
    return textRange != null ? textRange.end().lineOffset() : null;
  }

  @Override
  public Integer getEndLine() {
    return textRange != null ? textRange.end().line() : null;
  }

  @SuppressWarnings("unchecked")
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
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append("rule=").append(activeRule.ruleKey());
    sb.append(", severity=").append(severity);
    if (textRange != null) {
      sb.append(", line=").append(textRange.start().line());
    }
    if (clientInputFile != null) {
      sb.append(", file=").append(clientInputFile.getPath());
    }
    sb.append("]");
    return sb.toString();
  }

  private class DefaultLocation implements org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation {
    private final TextRange textRange;
    private final String message;

    private DefaultLocation(@Nullable TextRange textRange, @Nullable String message) {
      this.textRange = textRange;
      this.message = message;
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
    public Integer getEndLineOffset() {
      return textRange != null ? textRange.end().lineOffset() : null;
    }

    @Override
    public Integer getEndLine() {
      return textRange != null ? textRange.end().line() : null;
    }

    @Override
    public String getMessage() {
      return message;
    }
  }

  private class DefaultFlow implements Flow {
    private List<org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation> locations;

    private DefaultFlow(List<IssueLocation> issueLocations) {
      this.locations = Lists.transform(issueLocations, i -> new DefaultLocation(i.textRange(), i.message()));
    }

    @Override
    public List<org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation> locations() {
      return locations;
    }
  }
}
