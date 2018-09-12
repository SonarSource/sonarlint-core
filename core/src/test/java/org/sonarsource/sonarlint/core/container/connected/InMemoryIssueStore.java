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
package org.sonarsource.sonarlint.core.container.connected;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;

public class InMemoryIssueStore implements IssueStore {
  private Map<String, List<ServerIssue>> issuesMap;

  @Override
  public void save(List<ServerIssue> issues) {
    issuesMap = issues.stream().collect(Collectors.groupingBy(ServerIssue::getPath));
  }

  @Override
  public List<ServerIssue> load(String sqFilePath) {
    List<ServerIssue> list = issuesMap.get(sqFilePath);
    return list == null ? Collections.emptyList() : list;
  }

  @Override
  public void delete(String sqFilePath) {
    issuesMap.remove(sqFilePath);
  }
}
