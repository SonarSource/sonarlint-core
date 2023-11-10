/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssueStorePathsTests {

  @Test
  void local_path_to_sq_path_uses_both_prefixes() {
    var projectBinding = new ProjectBinding("project", "sq", "ide");
    var sqPath = IssueStorePaths.idePathToServerPath(projectBinding, "ide/project1/path1");
    assertThat(sqPath).isEqualTo("sq/project1/path1");
  }

  @Test
  void local_path_to_fileKey() {
    var projectBinding = new ProjectBinding("projectKey", "project", "ide");
    var fileKey = IssueStorePaths.idePathToFileKey(projectBinding, "ide/B/path1");
    assertThat(fileKey).isEqualTo("projectKey:project/B/path1");
  }

  @Test
  void local_path_to_sq_path_returns_null_if_path_doesnt_match_prefix() {
    var projectBinding = new ProjectBinding("project", "sq", "ide");
    var sqPath = IssueStorePaths.idePathToServerPath(projectBinding, "unknown/project1/path1");
    assertThat(sqPath).isNull();
  }

  @Test
  void local_path_to_sq_path_returns_null_if_path_match_prefix_partially() {
    var projectBinding = new ProjectBinding("project", "sq", "src");
    var sqPath = IssueStorePaths.idePathToServerPath(projectBinding, "src2/project1/path1");
    assertThat(sqPath).isNull();
  }

  @Test
  void local_path_to_sq_path_without_sq_prefix() {
    var projectBinding = new ProjectBinding("project", "", "ide");
    var sqPath = IssueStorePaths.idePathToServerPath(projectBinding, "ide/project1/path1");
    assertThat(sqPath).isEqualTo("project1/path1");
  }

  @Test
  void local_path_to_sq_path_without_ide_prefix() {
    var projectBinding = new ProjectBinding("project", "sq", "");
    var sqPath = IssueStorePaths.idePathToServerPath(projectBinding, "ide/project1/path1");
    assertThat(sqPath).isEqualTo("sq/ide/project1/path1");
  }

  @Test
  void local_path_to_fileKey_returns_null_if_path_doesnt_match_prefix() {
    var projectBinding = new ProjectBinding("project", "project", "ide");
    var fileKey = IssueStorePaths.idePathToFileKey(projectBinding, "unknown/B/path1");
    assertThat(fileKey).isNull();
  }

}
