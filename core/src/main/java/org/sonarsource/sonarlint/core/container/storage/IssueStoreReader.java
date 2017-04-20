/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.model.DefaultServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;

public class IssueStoreReader {
  private final IssueStoreFactory issueStoreFactory;
  private final StorageManager storageManager;

  public IssueStoreReader(IssueStoreFactory issueStoreFactory, StorageManager storageManager) {
    this.issueStoreFactory = issueStoreFactory;
    this.storageManager = storageManager;
  }

  public List<ServerIssue> getServerIssues(ProjectId projectId, String filePath) {
    String fileKey = getFileKey(projectId, filePath);

    Path serverIssuesPath = storageManager.getServerIssuesPath(projectId);
    IssueStore issueStore = issueStoreFactory.apply(serverIssuesPath);

    List<ScannerInput.ServerIssue> loadedIssues = issueStore.load(fileKey);

    return loadedIssues.stream()
      .map(pbIssue -> transformIssue(pbIssue, projectId, filePath))
      .collect(Collectors.toList());
  }

  public String getFileKey(ProjectId projectId, String filePath) {
    ModuleConfiguration moduleConfig = storageManager.readProjectConfigFromStorage(projectId);

    if (moduleConfig == null) {
      // unknown module
      throw new IllegalStateException("module not in storage: " + projectId);
    }

    Map<String, String> modulePaths = moduleConfig.getModulePathByKeyMap();

    // find longest prefix match
    String subModuleKey = projectId.getProjectKey();
    int prefixLen = 0;

    for (Map.Entry<String, String> entry : modulePaths.entrySet()) {
      String entryModuleKey = entry.getKey();
      String entryPath = entry.getValue();
      if (filePath.startsWith(entryPath) && prefixLen < entryPath.length()) {
        subModuleKey = entryModuleKey;
        prefixLen = entryPath.length() + 1;
      }
    }

    String relativeFilePath = filePath.substring(prefixLen);
    return subModuleKey + ":" + relativeFilePath;
  }

  private static ServerIssue transformIssue(ScannerInput.ServerIssue pbIssue, ProjectId projectId, String filePath) {
    DefaultServerIssue issue = new DefaultServerIssue();
    issue.setAssigneeLogin(pbIssue.getAssigneeLogin());
    issue.setAssigneeLogin(pbIssue.getAssigneeLogin());
    issue.setChecksum(pbIssue.getChecksum());
    issue.setLine(pbIssue.getLine());
    issue.setFilePath(filePath);
    issue.setProjectId(projectId);
    issue.setManualSeverity(pbIssue.getManualSeverity());
    issue.setMessage(pbIssue.getMsg());
    issue.setSeverity(pbIssue.getSeverity().name());
    issue.setCreationDate(Instant.ofEpochMilli(pbIssue.getCreationDate()));
    issue.setResolution(pbIssue.getResolution());
    issue.setKey(pbIssue.getKey());
    issue.setRuleKey(pbIssue.getRuleRepository() + ":" + pbIssue.getRuleKey());
    return issue;
  }
}
