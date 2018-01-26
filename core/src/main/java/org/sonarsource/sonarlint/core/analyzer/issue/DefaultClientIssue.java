/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.analyzer.issue;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public final class DefaultClientIssue extends TextRangeLocation implements org.sonarsource.sonarlint.core.client.api.common.analysis.Issue {
  private final String severity;
  private final String type;
  private final ActiveRule activeRule;
  private final String primaryMessage;
  private final ClientInputFile clientInputFile;
  private final Rule rule;
  private final List<Flow> flows;

  public DefaultClientIssue(String severity, @Nullable String type, ActiveRule activeRule, Rule rule, String primaryMessage, @Nullable TextRange textRange,
    @Nullable ClientInputFile clientInputFile, List<org.sonar.api.batch.sensor.issue.Issue.Flow> flows) {
    super(textRange);
    this.severity = severity;
    this.type = type;
    this.activeRule = activeRule;
    this.rule = rule;
    this.primaryMessage = primaryMessage;
    this.clientInputFile = clientInputFile;
    this.flows = flows.stream().map(f -> new DefaultFlow(f.locations())).collect(Collectors.toList());
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

  private static class DefaultLocation extends TextRangeLocation {
    private final String message;

    private DefaultLocation(@Nullable TextRange textRange, @Nullable String message) {
      super(textRange);
      this.message = message;
    }

    @Override
    public String getMessage() {
      return message;
    }
  }

  private static class DefaultFlow implements Flow {
    private List<org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation> locations;

    private DefaultFlow(List<IssueLocation> issueLocations) {
      this.locations = issueLocations.stream()
        .map(i -> new DefaultLocation(i.textRange(), i.message()))
        .collect(Collectors.toList());
    }

    @Override
    public List<org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation> locations() {
      return locations;
    }
  }
}
