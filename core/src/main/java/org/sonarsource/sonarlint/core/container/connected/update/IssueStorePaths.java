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

import java.time.Instant;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.container.model.DefaultServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class IssueStorePaths {

  public String sqPathToFileKey(Sonarlint.ProjectConfiguration projectConfiguration, String projectKey, String sqFilePath) {
    Map<String, String> modulePaths = projectConfiguration.getModulePathByKeyMap();

    // find longest prefix match
    String subModuleKey = projectKey;
    int prefixLen = 0;

    for (Map.Entry<String, String> entry : modulePaths.entrySet()) {
      String entryModuleKey = entry.getKey();
      String entryPath = entry.getValue();
      if (!entryPath.isEmpty() && sqFilePath.startsWith(entryPath) && prefixLen <= entryPath.length()) {
        subModuleKey = entryModuleKey;
        prefixLen = entryPath.length() + 1;
      }
    }

    String relativeFilePath = sqFilePath.substring(prefixLen);
    return subModuleKey + ":" + relativeFilePath;
  }

  @CheckForNull
  public String localPathToFileKey(Sonarlint.ProjectConfiguration projectConfiguration, ProjectBinding projectBinding, String localFilePath) {
    String sqFilePath = sqPathToLocalPath(projectBinding, localFilePath);

    if (sqFilePath == null) {
      return null;
    }
    return sqPathToFileKey(projectConfiguration, projectBinding.projectKey(), sqFilePath);
  }

  @CheckForNull
  public String localPathToSqPath(ProjectBinding projectBinding, String localFilePath) {
    if (!localFilePath.startsWith(projectBinding.localPathPrefix())) {
      return null;
    }
    int localPrefixLen = projectBinding.localPathPrefix().length();
    return projectBinding.sqPathPrefix() + localFilePath.substring(localPrefixLen);
  }

  @CheckForNull
  public String sqPathToLocalPath(ProjectBinding projectBinding, String sqFilePath) {
    if (!sqFilePath.startsWith(projectBinding.sqPathPrefix())) {
      return null;
    }
    return projectBinding.localPathPrefix() + sqFilePath.substring(0, projectBinding.sqPathPrefix().length());
  }

  public Sonarlint.ServerIssue toStorageIssue(ScannerInput.ServerIssue issue, Sonarlint.ProjectConfiguration projectConfiguration) {
    String sqPath = fileKeyToSqPath(projectConfiguration, issue.getModuleKey(), issue.getPath());

    Sonarlint.ServerIssue.Builder builder = Sonarlint.ServerIssue.newBuilder()
      .setAssigneeLogin(issue.getAssigneeLogin())
      .setChecksum(issue.getChecksum())
      .setLine(issue.getLine())
      .setPath(sqPath)
      .setManualSeverity(issue.getManualSeverity())
      .setMsg(issue.getMsg())
      .setSeverity(issue.getSeverity().name());

    if (issue.hasType()) {
      // type was added recently
      builder.setType(issue.getType());
    }
    builder.setCreationDate(issue.getCreationDate())
      .setResolution(issue.getResolution())
      .setKey(issue.getKey());

    return builder.build();
  }

  public String fileKeyToSqPath(Sonarlint.ProjectConfiguration projectConfiguration, String fileModuleKey, String filePath) {
    Map<String, String> modulePaths = projectConfiguration.getModulePathByKeyMap();

    // normally this should not be null, but the ModuleConfiguration could be out dated
    String modulePath = modulePaths.get(fileModuleKey);
    if (modulePath == null) {
      modulePath = "";
    }

    return modulePath + filePath;
  }

  public static ServerIssue toApiIssue(Sonarlint.ServerIssue pbIssue, String localPath) {
    DefaultServerIssue issue = new DefaultServerIssue();
    issue.setAssigneeLogin(pbIssue.getAssigneeLogin());
    issue.setChecksum(pbIssue.getChecksum());
    issue.setModuleKey(pbIssue.getModuleKey());
    issue.setLine(pbIssue.getLine());
    issue.setFilePath(localPath);
    issue.setManualSeverity(pbIssue.getManualSeverity());
    issue.setMessage(pbIssue.getMsg());
    issue.setSeverity(pbIssue.getSeverity());
    // type was added recently
    issue.setType(pbIssue.getType().isEmpty() ? null : pbIssue.getType());
    issue.setCreationDate(Instant.ofEpochMilli(pbIssue.getCreationDate()));
    issue.setResolution(pbIssue.getResolution());
    issue.setKey(pbIssue.getKey());
    issue.setRuleKey(pbIssue.getRuleRepository() + ":" + pbIssue.getRuleKey());
    return issue;
  }
}
