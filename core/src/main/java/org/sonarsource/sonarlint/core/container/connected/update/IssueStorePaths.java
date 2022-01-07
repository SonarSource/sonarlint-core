/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.time.Instant;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.analysis.api.TextRange;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.container.model.DefaultServerFlow;
import org.sonarsource.sonarlint.core.container.model.DefaultServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public class IssueStorePaths {

  public String sqPathToFileKey(Sonarlint.ProjectConfiguration projectConfiguration, String projectKey, String sqFilePath) {
    var modulePaths = projectConfiguration.getModulePathByKeyMap();

    // find longest prefix match
    var subModuleKey = projectKey;
    var prefixLen = 0;

    for (Map.Entry<String, String> entry : modulePaths.entrySet()) {
      var entryModuleKey = entry.getKey();
      var entryPath = entry.getValue();
      if (!entryPath.isEmpty() && sqFilePath.startsWith(entryPath) && prefixLen <= entryPath.length()) {
        subModuleKey = entryModuleKey;
        prefixLen = entryPath.length() + 1;
      }
    }

    var relativeFilePath = sqFilePath.substring(prefixLen);
    return subModuleKey + ":" + relativeFilePath;
  }

  @CheckForNull
  public String idePathToFileKey(Sonarlint.ProjectConfiguration projectConfiguration, ProjectBinding projectBinding, String ideFilePath) {
    var sqFilePath = idePathToSqPath(projectBinding, ideFilePath);

    if (sqFilePath == null) {
      return null;
    }
    return sqPathToFileKey(projectConfiguration, projectBinding.projectKey(), sqFilePath);
  }

  @CheckForNull
  public String idePathToSqPath(ProjectBinding projectBinding, String ideFilePath) {
    if (!ideFilePath.startsWith(projectBinding.idePathPrefix())) {
      return null;
    }
    var localPrefixLen = projectBinding.idePathPrefix().length();
    if (localPrefixLen > 0) {
      localPrefixLen++;
    }
    var sqPathPrefix = projectBinding.sqPathPrefix();
    if (!sqPathPrefix.isEmpty()) {
      sqPathPrefix = sqPathPrefix + "/";
    }
    return sqPathPrefix + ideFilePath.substring(localPrefixLen);
  }

  public String fileKeyToSqPath(Sonarlint.ProjectConfiguration projectConfiguration, String fileModuleKey, String filePath) {
    var modulePaths = projectConfiguration.getModulePathByKeyMap();

    // normally this should not be null, but the ModuleConfiguration could be out dated
    var modulePath = modulePaths.getOrDefault(fileModuleKey, "");
    if (!modulePath.isEmpty()) {
      modulePath = modulePath + "/";
    }
    return modulePath + filePath;
  }

  public static ServerIssue toApiIssue(Sonarlint.ServerIssue pbIssue, String idePath) {
    var issue = new DefaultServerIssue();
    issue.setAssigneeLogin(pbIssue.getAssigneeLogin());
    issue.setLineHash(pbIssue.getLineHash());
    if (pbIssue.getPrimaryLocation().hasTextRange()) {
      var textRange = pbIssue.getPrimaryLocation().getTextRange();
      issue.setTextRange(new TextRange(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset()));
      issue.setCodeSnippet(trimToNull(pbIssue.getPrimaryLocation().getCodeSnippet()));
    }
    issue.setFilePath(idePath);
    issue.setMessage(pbIssue.getPrimaryLocation().getMsg());
    issue.setSeverity(pbIssue.getSeverity());
    issue.setType(pbIssue.getType());
    issue.setCreationDate(Instant.ofEpochMilli(pbIssue.getCreationDate()));
    issue.setResolution(pbIssue.getResolution());
    issue.setKey(pbIssue.getKey());
    issue.setRuleKey(pbIssue.getRuleRepository() + ":" + pbIssue.getRuleKey());
    for (Sonarlint.ServerIssue.Flow f : pbIssue.getFlowList()) {
      issue.getFlows().add(new DefaultServerFlow(f.getLocationList()));
    }
    return issue;
  }
}
