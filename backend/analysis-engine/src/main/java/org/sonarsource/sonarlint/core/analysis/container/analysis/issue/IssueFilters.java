/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.Optional;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.scan.issue.filter.FilterableIssue;
import org.sonar.api.scan.issue.filter.IssueFilter;
import org.sonar.api.scan.issue.filter.IssueFilterChain;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultFilterableIssue;

@SonarLintSide
public class IssueFilters {
  private final List<IssueFilter> filters;

  public IssueFilters(Optional<List<IssueFilter>> exclusionFilters) {
    this.filters = exclusionFilters.orElse(List.of());
  }

  public boolean accept(InputComponent inputComponent, Issue rawIssue) {
    IssueFilterChain filterChain = new DefaultIssueFilterChain(filters);
    FilterableIssue fIssue = new DefaultFilterableIssue(rawIssue, inputComponent);
    return filterChain.accept(fIssue);
  }

}
