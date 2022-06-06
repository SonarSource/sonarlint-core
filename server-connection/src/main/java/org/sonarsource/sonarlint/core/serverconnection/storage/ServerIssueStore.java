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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.util.List;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssue;

public interface ServerIssueStore {

  /**
   * Store issues per file.
   * For filesystem-based implementations, watch out for:
   * - Too long paths
   * - Directories with too many files
   * - (Too deep paths?)
   */
  void save(String projectKey, List<ServerIssue> issues);

  /**
   * Load issues stored for specified file.
   *
   *
   * @param projectKey
   * @param sqFilePath the relative path to the base of project, in SonarQube
   * @return issues, possibly empty
   */
  List<ServerIssue> load(String projectKey, String sqFilePath);

  void close();
}
