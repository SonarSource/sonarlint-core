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
package org.sonarsource.sonarlint.core.container.connected.update;

import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

import static org.assertj.core.api.Assertions.assertThat;

public class IssueStorePathsTest {
  private final IssueStorePaths issueStorePaths = new IssueStorePaths();

  @Test
  public void local_path_to_sq_path_uses_both_prefixes() {
    ProjectBinding projectBinding = new ProjectBinding("project", "sq", "ide");
    String sqPath = issueStorePaths.idePathToSqPath(projectBinding, "ide/project1/path1");
    assertThat(sqPath).isEqualTo("sq/project1/path1");
  }

  @Test
  public void sq_path_to_fileKey_uses_most_specific_module() {
    Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder()
      .putModulePathByKey("root", "project")
      .putModulePathByKey("moduleA", "project/A")
      .putModulePathByKey("moduleB", "project/B")
      .build();
    String fileKey = issueStorePaths.sqPathToFileKey(projectConfiguration, "projectKey", "project/A/path1");

    assertThat(fileKey).isEqualTo("moduleA:path1");
  }

  @Test
  public void sq_path_to_fileKey_uses_projectKey_if_no_module_found() {
    Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder()
      .putModulePathByKey("root", "project")
      .putModulePathByKey("moduleA", "project/A")
      .putModulePathByKey("moduleB", "project/B")
      .build();
    String fileKey = issueStorePaths.sqPathToFileKey(projectConfiguration, "projectKey", "unknown/path1");
    assertThat(fileKey).isEqualTo("projectKey:unknown/path1");
  }

  @Test
  public void local_path_to_fileKey_uses_modules_and_prefixes() {
    ProjectBinding projectBinding = new ProjectBinding("project", "project", "ide");
    Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder()
      .putModulePathByKey("root", "project")
      .putModulePathByKey("moduleA", "project/A")
      .putModulePathByKey("moduleB", "project/B")
      .build();
    String fileKey = issueStorePaths.idePathToFileKey(projectConfiguration, projectBinding, "ide/B/path1");
    assertThat(fileKey).isEqualTo("moduleB:path1");
  }

  @Test
  public void local_path_to_sq_path_returns_null_if_path_doesnt_match_prefix() {
    ProjectBinding projectBinding = new ProjectBinding("project", "sq", "ide");
    String sqPath = issueStorePaths.idePathToSqPath(projectBinding, "unknown/project1/path1");
    assertThat(sqPath).isNull();
  }

  @Test
  public void local_path_to_sq_path_without_sq_prefix() {
    ProjectBinding projectBinding = new ProjectBinding("project", "", "ide");
    String sqPath = issueStorePaths.idePathToSqPath(projectBinding, "ide/project1/path1");
    assertThat(sqPath).isEqualTo("project1/path1");
  }

  @Test
  public void local_path_to_sq_path_without_ide_prefix() {
    ProjectBinding projectBinding = new ProjectBinding("project", "sq", "");
    String sqPath = issueStorePaths.idePathToSqPath(projectBinding, "ide/project1/path1");
    assertThat(sqPath).isEqualTo("sq/ide/project1/path1");
  }

  @Test
  public void local_path_to_fileKey_returns_null_if_path_doesnt_match_prefix() {
    ProjectBinding projectBinding = new ProjectBinding("project", "project", "ide");
    Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder()
      .putModulePathByKey("root", "project")
      .putModulePathByKey("moduleA", "project/A")
      .putModulePathByKey("moduleB", "project/B")
      .build();
    String fileKey = issueStorePaths.idePathToFileKey(projectConfiguration, projectBinding, "unknown/B/path1");
    assertThat(fileKey).isNull();
  }

}
