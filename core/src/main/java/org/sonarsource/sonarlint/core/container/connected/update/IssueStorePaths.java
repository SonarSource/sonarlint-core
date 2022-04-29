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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import javax.annotation.CheckForNull;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.analysis.api.TextRange;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.container.model.DefaultServerFlow;
import org.sonarsource.sonarlint.core.container.model.DefaultServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public class IssueStorePaths {

  @CheckForNull
  public String idePathToFileKey(ProjectBinding projectBinding, String ideFilePath) {
    var sqFilePath = idePathToSqPath(projectBinding, ideFilePath);

    if (sqFilePath == null) {
      return null;
    }
    return projectBinding.projectKey() + ":" + sqFilePath;
  }

  @CheckForNull
  public String idePathToSqPath(ProjectBinding projectBinding, String ideFilePathStr) {
    var ideFilePath = Paths.get(ideFilePathStr);
    Path commonPart;
    if (StringUtils.isNotEmpty(projectBinding.idePathPrefix())) {
      var idePathPrefix = Paths.get(projectBinding.idePathPrefix());
      if (!ideFilePath.startsWith(idePathPrefix)) {
        return null;
      }
      commonPart = idePathPrefix.relativize(ideFilePath);
    } else {
      commonPart = ideFilePath;
    }
    if (StringUtils.isNotEmpty(projectBinding.sqPathPrefix())) {
      var sqPathPrefix = Paths.get(projectBinding.sqPathPrefix());
      return FilenameUtils.separatorsToUnix(sqPathPrefix.resolve(commonPart).toString());
    } else {
      return FilenameUtils.separatorsToUnix(commonPart.toString());
    }
  }

  public static ServerIssue toApiIssue(Sonarlint.ServerIssue pbIssue, String idePath) {
    var issue = new DefaultServerIssue();
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
