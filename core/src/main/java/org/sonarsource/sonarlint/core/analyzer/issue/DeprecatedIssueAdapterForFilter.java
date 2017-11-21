/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.IssueComment;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.Duration;

/**
 * @deprecated since 5.3
 */
@Deprecated
class DeprecatedIssueAdapterForFilter implements Issue {
  private final Project project;
  private final DefaultClientIssue rawIssue;
  private final String componentKey;

  DeprecatedIssueAdapterForFilter(Project project, DefaultClientIssue rawIssue, String componentKey) {
    this.project = project;
    this.rawIssue = rawIssue;
    this.componentKey = componentKey;
  }

  @Override
  public String key() {
    throw unsupported();
  }

  @Override
  public String componentKey() {
    return componentKey;
  }

  @Override
  public RuleKey ruleKey() {
    return RuleKey.parse(rawIssue.getRuleKey());
  }

  @Override
  public String language() {
    throw unsupported();
  }

  @Override
  public String severity() {
    return rawIssue.getSeverity();
  }

  @Override
  public String message() {
    return rawIssue.getMessage();
  }

  @Override
  public Integer line() {
    return rawIssue.getStartLine();
  }

  @Override
  @Deprecated
  public Double effortToFix() {
    throw unsupported();
  }

  @Override
  public String status() {
    return Issue.STATUS_OPEN;
  }

  @Override
  public String resolution() {
    return null;
  }

  @Override
  public String reporter() {
    throw unsupported();
  }

  @Override
  public String assignee() {
    return null;
  }

  @Override
  public Date creationDate() {
    return new Date();
  }

  @Override
  public Date updateDate() {
    return null;
  }

  @Override
  public Date closeDate() {
    return null;
  }

  @Override
  public String attribute(String key) {
    return attributes().get(key);
  }

  @Override
  public Map<String, String> attributes() {
    return Collections.emptyMap();
  }

  @Override
  public String authorLogin() {
    throw unsupported();
  }

  @Override
  public String actionPlanKey() {
    throw unsupported();
  }

  @Override
  public List<IssueComment> comments() {
    throw unsupported();
  }

  @Override
  public boolean isNew() {
    throw unsupported();
  }

  @Deprecated
  @Override
  public Duration debt() {
    throw unsupported();
  }

  @Override
  public String projectKey() {
    return project.getEffectiveKey();
  }

  @Override
  public String projectUuid() {
    throw unsupported();
  }

  @Override
  public String componentUuid() {
    throw unsupported();
  }

  @Override
  public Collection<String> tags() {
    throw unsupported();
  }

  private static UnsupportedOperationException unsupported() {
    return new UnsupportedOperationException("Not available for issues filters");
  }

  @Override
  public Double gap() {
    return null;
  }

  @Override
  public boolean isCopied() {
    return false;
  }

  @Override
  public Duration effort() {
    throw unsupported();
  }
}
