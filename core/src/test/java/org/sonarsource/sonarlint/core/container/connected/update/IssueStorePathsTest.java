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
package org.sonarsource.sonarlint.core.container.connected.update;

import org.junit.Test;
import org.sonar.scanner.protocol.Constants;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

import static org.assertj.core.api.Assertions.assertThat;

public class IssueStorePathsTest {
  private IssueStorePaths issueStorePaths = new IssueStorePaths();

  @Test
  public void local_path_to_sq_path_uses_both_prefixes() {
    ProjectBinding projectBinding = new ProjectBinding("project", "sq", "ide");
    String sqPath = issueStorePaths.localPathToSqPath(projectBinding, "ide/project1/path1");
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
    String fileKey = issueStorePaths.localPathToFileKey(projectConfiguration, projectBinding, "ide/B/path1");
    assertThat(fileKey).isEqualTo("moduleB:path1");
  }

  @Test
  public void local_path_to_sq_path_returns_null_if_path_doesnt_match_prefix() {
    ProjectBinding projectBinding = new ProjectBinding("project", "sq", "ide");
    String sqPath = issueStorePaths.localPathToSqPath(projectBinding, "unknown/project1/path1");
    assertThat(sqPath).isNull();
  }

  @Test
  public void local_path_to_sq_path_without_sq_prefix() {
    ProjectBinding projectBinding = new ProjectBinding("project", "", "ide");
    String sqPath = issueStorePaths.localPathToSqPath(projectBinding, "ide/project1/path1");
    assertThat(sqPath).isEqualTo("project1/path1");
  }

  @Test
  public void local_path_to_sq_path_without_ide_prefix() {
    ProjectBinding projectBinding = new ProjectBinding("project", "sq", "");
    String sqPath = issueStorePaths.localPathToSqPath(projectBinding, "ide/project1/path1");
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
    String fileKey = issueStorePaths.localPathToFileKey(projectConfiguration, projectBinding, "unknown/B/path1");
    assertThat(fileKey).isNull();
  }

  @Test
  public void to_storage_issue() {
    ScannerInput.ServerIssue issue = ScannerInput.ServerIssue.newBuilder()
      .setModuleKey("moduleA")
      .setPath("path")
      .setKey("key")
      .setRuleKey("ruleKey")
      .setAssigneeLogin("login")
      .setChecksum("checksum")
      .setMsg("msg")
      .setSeverity(Constants.Severity.BLOCKER)
      .setLine(10)
      .setPath("path")
      .setResolution("resolution")
      .setType("BUG")
      .setStatus("OPEN")
      .setCreationDate(1000L)
      .build();

    Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder()
      .putModulePathByKey("root", "project")
      .putModulePathByKey("moduleA", "project/A")
      .putModulePathByKey("moduleB", "project/B")
      .build();

    Sonarlint.ServerIssue serverIssue = issueStorePaths.toStorageIssue(issue, projectConfiguration);
    assertThat(serverIssue.getPath()).isEqualTo("project/A/path");
    assertThat(serverIssue.getType()).isEqualTo("BUG");
    assertThat(serverIssue.getAssigneeLogin()).isEqualTo("login");
    assertThat(serverIssue.getChecksum()).isEqualTo("checksum");
    assertThat(serverIssue.getCreationDate()).isEqualTo(1000L);
    assertThat(serverIssue.getKey()).isEqualTo("key");
    assertThat(serverIssue.getMsg()).isEqualTo("msg");
    assertThat(serverIssue.getResolution()).isEqualTo("resolution");
    assertThat(serverIssue.getStatus()).isEqualTo("OPEN");
    assertThat(serverIssue.getRuleKey()).isEqualTo("ruleKey");
    assertThat(serverIssue.getSeverity()).isEqualTo("BLOCKER");
  }
}
