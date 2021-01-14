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
import org.sonarqube.ws.Common.Flow;
import org.sonarqube.ws.Common.TextRange;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.Issues.Component;
import org.sonarqube.ws.Issues.Issue;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Location;
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
    Sonarlint.ServerIssue.Builder issueBuilder = Sonarlint.ServerIssue.newBuilder();
    Location.Builder locationBuilder = Location.newBuilder();
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder = Sonarlint.ServerIssue.TextRange.newBuilder();
    Sonarlint.ServerIssue.Flow.Builder flowBuilder = Sonarlint.ServerIssue.Flow.newBuilder();
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
      issue -> result.add(convertWsIssue(projectConfiguration, issueBuilder, locationBuilder, textRangeBuilder, flowBuilder, issue, componentsByKey)),
      true,
      progress);

    return result;
  }

  private ServerIssue convertWsIssue(ProjectConfiguration projectConfiguration, Sonarlint.ServerIssue.Builder issueBuilder, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, Sonarlint.ServerIssue.Flow.Builder flowBuilder, Issue issueFromWs,
    Map<String, Component> componentsByKey) {
    issueBuilder.clear();
    RuleKey ruleKey = RuleKey.parse(issueFromWs.getRule());
    Location primary = buildPrimaryLocation(projectConfiguration, locationBuilder, textRangeBuilder, issueFromWs, componentsByKey);
    issueBuilder
      .setAssigneeLogin(issueFromWs.getAssignee())
      .setChecksum(issueFromWs.getHash())
      .setCreationDate(org.sonar.api.utils.DateUtils.parseDateTime(issueFromWs.getCreationDate()).getTime())
      .setKey(issueFromWs.getKey())
      .setPrimaryLocation(primary)
      .setResolution(issueFromWs.getResolution())
      .setRuleKey(ruleKey.rule())
      .setRuleRepository(ruleKey.repository())
      .setSeverity(issueFromWs.getSeverity().name())
      .setStatus(issueFromWs.getStatus());

    buildFlows(projectConfiguration, issueBuilder, locationBuilder, textRangeBuilder, flowBuilder, issueFromWs, componentsByKey);

    if (issueFromWs.hasType()) {
      // type was added recently
      issueBuilder.setType(issueFromWs.getType().name());
    }

    return issueBuilder.build();
  }

  private void buildFlows(ProjectConfiguration projectConfiguration, Sonarlint.ServerIssue.Builder issueBuilder, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, Sonarlint.ServerIssue.Flow.Builder flowBuilder, Issue issueFromWs, Map<String, Component> componentsByKey) {
    for (Flow flowFromWs : issueFromWs.getFlowsList()) {
      flowBuilder.clear();

      for (org.sonarqube.ws.Common.Location locationFromWs : flowFromWs.getLocationsList()) {
        Component component = componentsByKey.get(locationFromWs.getComponent());
        String sqPath = issueStorePaths.fileKeyToSqPath(projectConfiguration, issueFromWs.getSubProject(), component.getPath());
        locationBuilder.clear();
        locationBuilder.setPath(sqPath);
        locationBuilder.setMsg(locationFromWs.getMsg());
        copyTextRangeFromWs(locationBuilder, textRangeBuilder, locationFromWs.getTextRange());
        flowBuilder.addLocation(locationBuilder);
      }

      issueBuilder.addFlow(flowBuilder);
    }
  }

  private Location buildPrimaryLocation(ProjectConfiguration projectConfiguration, Location.Builder locationBuilder, Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder,
    Issue issueFromWs, Map<String, Component> componentsByKey) {
    Component component = componentsByKey.get(issueFromWs.getComponent());
    String sqPath = issueStorePaths.fileKeyToSqPath(projectConfiguration, issueFromWs.getSubProject(), component.getPath());
    locationBuilder.clear();
    locationBuilder.setPath(sqPath);
    locationBuilder.setMsg(issueFromWs.getMessage());
    copyTextRangeFromWs(locationBuilder, textRangeBuilder, issueFromWs.getTextRange());
    return locationBuilder.build();
  }

  private static void copyTextRangeFromWs(Location.Builder locationBuilder, Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, TextRange textRange) {
    textRangeBuilder.clear();
    textRangeBuilder.setStartLine(textRange.getStartLine());
    textRangeBuilder.setStartOffset(textRange.getStartOffset());
    textRangeBuilder.setEndLine(textRange.getEndLine());
    textRangeBuilder.setEndOffset(textRange.getEndOffset());
    locationBuilder.setTextRange(textRangeBuilder);
  }

  private static String getIssuesUrl(String key) {
    return "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=CODE_SMELL,BUG,VULNERABILITY&componentKeys=" + StringUtils.urlEncode(key);
  }
}
