/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scan.issue.filter.FilterableIssue;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextPointer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextRange;

public class DefaultFilterableIssue implements FilterableIssue {
  private final Issue rawIssue;
  private final InputComponent component;

  public DefaultFilterableIssue(Issue rawIssue, InputComponent component) {
    this.rawIssue = rawIssue;
    this.component = component;
  }

  @Override
  public String componentKey() {
    return component.key();
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
  public String projectKey() {
    throw unsupported();
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

  public InputComponent getComponent() {
    return component;
  }

  @Override
  public TextRange textRange() {
    var textRange = rawIssue.getTextRange();
    if (textRange == null) {
      return null;
    }
    return new DefaultTextRange(new DefaultTextPointer(textRange.getStartLine(), textRange.getStartLineOffset()),
      new DefaultTextPointer(textRange.getEndLine(), textRange.getEndLineOffset()));
  }

}
