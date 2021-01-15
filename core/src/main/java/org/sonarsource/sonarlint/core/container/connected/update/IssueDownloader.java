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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonar.api.rule.RuleKey;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.Issues.Component;
import org.sonarqube.ws.Issues.Issue;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class IssueDownloader {

  private final SonarLintWsClient wsClient;
  private final IssueStorePaths issueStorePaths;

  public IssueDownloader(SonarLintWsClient wsClient, IssueStorePaths issueStorePaths) {
    this.wsClient = wsClient;
    this.issueStorePaths = issueStorePaths;
  }

  /**
   * Fetch all issues of the component with specified key.
   * If the component doesn't exist or it exists but has no issues, an empty iterator is returned.
   *
   * @param key project key, or file key.
   * @return Iterator of issues. It can be empty but never null.
   */
  public List<Sonarlint.ServerIssue> download(String key, ProjectConfiguration projectConfiguration, ProgressWrapper progress) {
    StringBuilder searchUrl = new StringBuilder();
    searchUrl.append(getIssuesUrl(key));
    wsClient.getOrganizationKey()
      .ifPresent(org -> searchUrl.append("&organization=").append(StringUtils.urlEncode(org)));
    Sonarlint.ServerIssue.Builder builder = Sonarlint.ServerIssue.newBuilder();
    List<Sonarlint.ServerIssue> result = new ArrayList<>();
    Map<String, Component> componentsByKey = new HashMap<>();
    SonarLintWsClient.getPaginated(wsClient, searchUrl.toString(),
      Issues.SearchWsResponse::parseFrom,
      Issues.SearchWsResponse::getPaging,
      r -> {
        componentsByKey.clear();
        componentsByKey.putAll(r.getComponentsList().stream().collect(Collectors.toMap(Component::getKey, c -> c)));
        return r.getIssuesList();
      },
      issue -> result.add(convertWsIssue(projectConfiguration, builder, issue, componentsByKey)),
      true,
      progress);

    return result;
  }

  private ServerIssue convertWsIssue(ProjectConfiguration projectConfiguration, Sonarlint.ServerIssue.Builder builder, Issue issue, Map<String, Component> componentsByKey) {
    builder.clear();
    RuleKey ruleKey = RuleKey.parse(issue.getRule());
    Component component = componentsByKey.get(issue.getComponent());
    String sqPath = issueStorePaths.fileKeyToSqPath(projectConfiguration, issue.getSubProject(), component.getPath());
    builder
      .setAssigneeLogin(issue.getAssignee())
      .setChecksum(issue.getHash())
      .setCreationDate(org.sonar.api.utils.DateUtils.parseDateTime(issue.getCreationDate()).getTime())
      .setKey(issue.getKey())
      .setLine(issue.getLine())
      .setMsg(issue.getMessage())
      .setPath(sqPath)
      .setResolution(issue.getResolution())
      .setRuleKey(ruleKey.rule())
      .setRuleRepository(ruleKey.repository())
      .setSeverity(issue.getSeverity().name())
      .setStatus(issue.getStatus());

    if (issue.hasType()) {
      // type was added recently
      builder.setType(issue.getType().name());
    }

    return builder.build();
  }

  private static String getIssuesUrl(String key) {
    return "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=CODE_SMELL,BUG,VULNERABILITY&componentKeys=" + StringUtils.urlEncode(key);
  }
}
