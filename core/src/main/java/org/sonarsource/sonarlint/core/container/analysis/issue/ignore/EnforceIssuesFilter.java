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
package org.sonarsource.sonarlint.core.container.analysis.issue.ignore;

import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.scan.issue.filter.FilterableIssue;
import org.sonar.api.scan.issue.filter.IssueFilter;
import org.sonar.api.scan.issue.filter.IssueFilterChain;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analyzer.issue.DefaultFilterableIssue;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssueInclusionPatternInitializer;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssuePattern;

public class EnforceIssuesFilter implements IssueFilter {

  private IssueInclusionPatternInitializer patternInitializer;

  private static final Logger LOG = Loggers.get(EnforceIssuesFilter.class);

  public EnforceIssuesFilter(IssueInclusionPatternInitializer patternInitializer) {
    this.patternInitializer = patternInitializer;
  }

  @Override
  public boolean accept(FilterableIssue issue, IssueFilterChain chain) {
    InputComponent inputComponent = ((DefaultFilterableIssue) issue).getInputComponent();

    for (IssuePattern pattern : patternInitializer.getMulticriteriaPatterns()) {
      if (pattern.getRulePattern().match(issue.ruleKey().toString())
        && inputComponent.isFile()
        && !pattern.getPathPattern().match((InputFile) inputComponent)) {
        LOG.debug("Issue {} ignored by enforce pattern {}", issue, pattern);
        return false;
      }
    }

    return chain.accept(issue);
  }
}
