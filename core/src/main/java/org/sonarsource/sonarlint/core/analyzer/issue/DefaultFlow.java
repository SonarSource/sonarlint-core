/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analyzer.issue;

import java.util.List;
import java.util.stream.Collectors;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputFile;

public class DefaultFlow implements Flow {
  private List<org.sonarsource.sonarlint.core.analysis.api.IssueLocation> locations;

  public DefaultFlow(List<IssueLocation> issueLocations) {
    this.locations = issueLocations.stream()
      .map(i -> new DefaultLocation(
        i.inputComponent().isFile() ? ((SonarLintInputFile) i.inputComponent()).getClientInputFile() : null,
        i.textRange(),
        i.message()))
      .collect(Collectors.toList());
  }

  @Override
  public List<org.sonarsource.sonarlint.core.analysis.api.IssueLocation> locations() {
    return locations;
  }
}
