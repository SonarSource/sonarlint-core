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
package org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.scan.issue.filter.FilterableIssue;
import org.sonar.api.scan.issue.filter.IssueFilter;
import org.sonar.api.scan.issue.filter.IssueFilterChain;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.DefaultFilterableIssue;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssueInclusionPatternInitializer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssuePattern;

public class EnforceIssuesFilter implements IssueFilter {

  private static final Logger LOG = Loggers.get(EnforceIssuesFilter.class);

  private final List<IssuePattern> multicriteriaPatterns;

  public EnforceIssuesFilter(IssueInclusionPatternInitializer patternInitializer) {
    this.multicriteriaPatterns = Collections.unmodifiableList(new ArrayList<>(patternInitializer.getMulticriteriaPatterns()));
  }

  @Override
  public boolean accept(FilterableIssue issue, IssueFilterChain chain) {
    boolean atLeastOneRuleMatched = false;
    boolean atLeastOnePatternFullyMatched = false;
    IssuePattern matchingPattern = null;

    for (IssuePattern pattern : multicriteriaPatterns) {
      if (pattern.matchRule(issue.ruleKey())) {
        atLeastOneRuleMatched = true;
        InputComponent component = ((DefaultFilterableIssue) issue).getComponent();
        if (component.isFile()) {
          SonarLintInputFile file = (SonarLintInputFile) component;
          if (pattern.matchFile(file.relativePath())) {
            atLeastOnePatternFullyMatched = true;
            matchingPattern = pattern;
          }
        }
      }
    }

    if (atLeastOneRuleMatched) {
      if (atLeastOnePatternFullyMatched) {
        LOG.debug("Issue {} enforced by pattern {}", issue, matchingPattern);
      }
      return atLeastOnePatternFullyMatched;
    } else {
      return chain.accept(issue);
    }
  }
}
