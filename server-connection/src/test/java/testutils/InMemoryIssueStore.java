/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
package testutils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStore;

public class InMemoryIssueStore implements ServerIssueStore {
  private Map<String, Map<String, List<ServerIssue>>> issuesByFileByProject;

  @Override
  public void save(String projectKey, List<ServerIssue> issues) {
    issuesByFileByProject = Map.of(projectKey, issues.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath)));
  }

  @Override
  public List<ServerIssue> load(String projectKey, String sqFilePath) {
    return issuesByFileByProject.getOrDefault(projectKey, Map.of())
      .getOrDefault(sqFilePath, Collections.emptyList());
  }

  @Override
  public void close() {
    // nothing to do
  }
}
