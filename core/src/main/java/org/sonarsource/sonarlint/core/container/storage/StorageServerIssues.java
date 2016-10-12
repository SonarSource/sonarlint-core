/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.model.DefaultServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;

public class StorageServerIssues {
  private final IssueStoreFactory issueStoreFactory;
  private final StorageManager storageManager;

  public StorageServerIssues(IssueStoreFactory issueStoreFactory, StorageManager storageManager) {
    this.issueStoreFactory = issueStoreFactory;
    this.storageManager = storageManager;

  }

  public List<ServerIssue> getServerIssues(String moduleKey, String filePath) {
    String fileKey = getFileKey(moduleKey, filePath);

    Path serverIssuesPath = storageManager.getServerIssuesPath(moduleKey);
    IssueStore issueStore = issueStoreFactory.apply(serverIssuesPath);

    List<org.sonar.scanner.protocol.input.ScannerInput.ServerIssue> loadedIssues = issueStore.load(fileKey);

    return loadedIssues.stream()
      .map(pbIssue -> transformIssue(pbIssue, moduleKey, filePath))
      .collect(Collectors.toList());
  }

  private String getFileKey(String moduleKey, String filePath) {
    ModuleConfiguration moduleConfig = storageManager.readModuleConfigFromStorage(moduleKey);
    Map<String, String> moduleKeysByPath = moduleConfig.getModulesKeysByPath();

    // find longest prefix match
    String subModuleKey = moduleKey;
    String relativeFilePath = filePath;

    for (Map.Entry<String, String> e : moduleKeysByPath.entrySet()) {
      if (filePath.startsWith(e.getKey()) && (relativeFilePath == null || relativeFilePath.length() < e.getKey().length())) {
        subModuleKey = e.getValue();
        relativeFilePath = e.getKey();
      }
    }

    return subModuleKey + ":" + relativeFilePath;
  }

  private static ServerIssue transformIssue(org.sonar.scanner.protocol.input.ScannerInput.ServerIssue pbIssue, String moduleKey, String filePath) {
    DefaultServerIssue issue = new DefaultServerIssue();
    issue.setAssigneeLogin(pbIssue.getAssigneeLogin());
    issue.setAssigneeLogin(pbIssue.getAssigneeLogin());
    issue.setChecksum(pbIssue.getChecksum());
    issue.setLine(pbIssue.getLine());
    issue.setFilePath(filePath);
    issue.setModuleKey(moduleKey);
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
