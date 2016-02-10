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

import java.util.Date;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scan.issue.filter.FilterableIssue;

public class DefaultFilterableIssue implements FilterableIssue {
  private final org.sonar.api.batch.sensor.issue.Issue rawIssue;
  private final Project project;
  private final String componentKey;

  public DefaultFilterableIssue(Project project, org.sonar.api.batch.sensor.issue.Issue rawIssue, String componentKey) {
    this.project = project;
    this.rawIssue = rawIssue;
    this.componentKey = componentKey;

  }

  @Override
  public String componentKey() {
    return componentKey;
  }

  @Override
  public RuleKey ruleKey() {
    return rawIssue.ruleKey();
  }

  @Override
  public String severity() {
    throw unsupported();
  }

  @Override
  public String message() {
    throw unsupported();
  }

  @Override
  public Integer line() {
    TextRange textRange = rawIssue.primaryLocation().textRange();
    return textRange != null ? textRange.start().line() : null;
  }

  @Override
  public Double effortToFix() {
    return rawIssue.effortToFix();
  }

  @Override
  public Date creationDate() {
    return project.getAnalysisDate();
  }

  @Override
  public String projectKey() {
    return project.getEffectiveKey();
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  private static UnsupportedOperationException unsupported() {
    return new UnsupportedOperationException("Not available for issues filters");
  }

}
