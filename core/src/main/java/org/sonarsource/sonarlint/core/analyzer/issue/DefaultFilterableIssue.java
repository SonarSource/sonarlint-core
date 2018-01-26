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

import java.util.Date;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scan.issue.filter.FilterableIssue;

public class DefaultFilterableIssue implements FilterableIssue {
  private final DefaultClientIssue rawIssue;
  private final Project project;
  private final InputComponent inputComponent;

  public DefaultFilterableIssue(Project project, DefaultClientIssue rawIssue, InputComponent inputComponent) {
    this.project = project;
    this.rawIssue = rawIssue;
    this.inputComponent = inputComponent;

  }

  @Override
  public String componentKey() {
    return inputComponent.key();
  }

  @Override
  public RuleKey ruleKey() {
    return RuleKey.parse(rawIssue.getRuleKey());
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
    return rawIssue.getStartLine();
  }

  @Override
  public Double effortToFix() {
    throw unsupported();
  }

  @Override
  public Date creationDate() {
    return new Date();
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

  @Override
  public Double gap() {
    throw unsupported();
  }

  public InputComponent getInputComponent() {
    return inputComponent;
  }

}
