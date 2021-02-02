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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
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
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class IssueDownloader {

  private static final Logger LOG = Loggers.get(IssueDownloader.class);

  private static Set<String> TAINT_REPOS = new HashSet<>(
    Arrays.asList("roslyn.sonaranalyzer.security.cs", "javasecurity", "jssecurity", "tssecurity", "phpsecurity", "pythonsecurity"));

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
    List<Issue> issuesFromWs = new ArrayList<>();
    StringBuilder searchUrl = new StringBuilder();
    searchUrl.append(getIssuesUrl(key));
    wsClient.getOrganizationKey()
      .ifPresent(org -> searchUrl.append("&organization=").append(StringUtils.urlEncode(org)));
    Sonarlint.ServerIssue.Builder issueBuilder = Sonarlint.ServerIssue.newBuilder();
    Location.Builder locationBuilder = Location.newBuilder();
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder = Sonarlint.ServerIssue.TextRange.newBuilder();
    Sonarlint.ServerIssue.Flow.Builder flowBuilder = Sonarlint.ServerIssue.Flow.newBuilder();
    Map<String, Component> componentsByKey = new HashMap<>();
    SonarLintWsClient.getPaginated(wsClient, searchUrl.toString(),
      Issues.SearchWsResponse::parseFrom,
      Issues.SearchWsResponse::getPaging,
      r -> {
        componentsByKey.putAll(r.getComponentsList().stream().collect(Collectors.toMap(Component::getKey, c -> c)));
        return r.getIssuesList();
      },
      issuesFromWs::add,
      true,
      progress);

    Map<String, String[]> sourceCodeByKey = new HashMap<>();
    return issuesFromWs.stream()
      .map(i -> convertWsIssue(projectConfiguration, issueBuilder, locationBuilder, textRangeBuilder, flowBuilder, i, componentsByKey, sourceCodeByKey))
      .collect(toList());
  }

  private ServerIssue convertWsIssue(ProjectConfiguration projectConfiguration, Sonarlint.ServerIssue.Builder issueBuilder, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, Sonarlint.ServerIssue.Flow.Builder flowBuilder, Issue issueFromWs,
    Map<String, Component> componentsByKey, Map<String, String[]> sourceCodeByKey) {
    issueBuilder.clear();
    RuleKey ruleKey = RuleKey.parse(issueFromWs.getRule());
    boolean isTaintVulnerability = isTaintVulnerability(ruleKey);
    Location primary = buildPrimaryLocation(projectConfiguration, locationBuilder, textRangeBuilder, issueFromWs, componentsByKey, sourceCodeByKey, isTaintVulnerability);
    issueBuilder
      .setAssigneeLogin(issueFromWs.getAssignee())
      .setLineHash(issueFromWs.getHash())
      .setCreationDate(org.sonar.api.utils.DateUtils.parseDateTime(issueFromWs.getCreationDate()).getTime())
      .setKey(issueFromWs.getKey())
      .setPrimaryLocation(primary)
      .setResolution(issueFromWs.getResolution())
      .setRuleKey(ruleKey.rule())
      .setRuleRepository(ruleKey.repository())
      .setSeverity(issueFromWs.getSeverity().name())
      .setStatus(issueFromWs.getStatus());

    buildFlows(projectConfiguration, issueBuilder, locationBuilder, textRangeBuilder, flowBuilder, issueFromWs, componentsByKey, sourceCodeByKey, isTaintVulnerability);

    if (issueFromWs.hasType()) {
      // type was added recently
      issueBuilder.setType(issueFromWs.getType().name());
    }

    return issueBuilder.build();
  }

  private static boolean isTaintVulnerability(RuleKey ruleKey) {
    return TAINT_REPOS.contains(ruleKey.repository());
  }

  private void buildFlows(ProjectConfiguration projectConfiguration, Sonarlint.ServerIssue.Builder issueBuilder, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, Sonarlint.ServerIssue.Flow.Builder flowBuilder, Issue issueFromWs, Map<String, Component> componentsByKey,
    Map<String, String[]> sourceCodeByKey, boolean populateCodeSnippets) {
    for (Flow flowFromWs : issueFromWs.getFlowsList()) {
      flowBuilder.clear();

      for (org.sonarqube.ws.Common.Location locationFromWs : flowFromWs.getLocationsList()) {
        locationBuilder.clear();
        locationBuilder.setMsg(locationFromWs.getMsg());
        Component component = componentsByKey.get(locationFromWs.getComponent());
        String sqPath = issueStorePaths.fileKeyToSqPath(projectConfiguration, issueFromWs.getSubProject(), component.getPath());
        locationBuilder.setPath(sqPath);
        if (locationFromWs.hasTextRange()) {
          copyTextRangeFromWs(locationBuilder, textRangeBuilder, locationFromWs.getTextRange());
          setCodeSnippet(populateCodeSnippets, locationBuilder, locationFromWs.getComponent(), locationFromWs.getTextRange(), sourceCodeByKey);
        }
        flowBuilder.addLocation(locationBuilder);
      }

      issueBuilder.addFlow(flowBuilder);
    }
  }

  private Location buildPrimaryLocation(ProjectConfiguration projectConfiguration, Location.Builder locationBuilder, Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder,
    Issue issueFromWs, Map<String, Component> componentsByKey, Map<String, String[]> sourceCodeByKey, boolean populateCodeSnippets) {
    locationBuilder.clear();
    locationBuilder.setMsg(issueFromWs.getMessage());
    Component component = componentsByKey.get(issueFromWs.getComponent());
    String sqPath = issueStorePaths.fileKeyToSqPath(projectConfiguration, issueFromWs.getSubProject(), component.getPath());
    locationBuilder.setPath(sqPath);
    if (issueFromWs.hasTextRange()) {
      copyTextRangeFromWs(locationBuilder, textRangeBuilder, issueFromWs.getTextRange());
      setCodeSnippet(populateCodeSnippets, locationBuilder, issueFromWs.getComponent(), issueFromWs.getTextRange(), sourceCodeByKey);
    }
    return locationBuilder.build();
  }

  private void setCodeSnippet(boolean populateCodeSnippets, Location.Builder locationBuilder, String fileKey, TextRange textRange, Map<String, String[]> sourceCodeByKey) {
    if (populateCodeSnippets) {
      String[] sourceCodeLines = getOrFetchSourceCode(fileKey, sourceCodeByKey);
      if (sourceCodeLines.length == 0) {
        return;
      }
      String[] linesOfTextRange;
      try {
        if (textRange.getStartLine() == textRange.getEndLine()) {
          String fullline = sourceCodeLines[textRange.getStartLine() - 1];
          locationBuilder.setCodeSnippet(fullline.substring(textRange.getStartOffset(), textRange.getEndOffset()));
        } else {
          linesOfTextRange = Arrays.copyOfRange(sourceCodeLines, textRange.getStartLine() - 1, textRange.getEndLine());
          linesOfTextRange[0] = linesOfTextRange[0].substring(textRange.getStartOffset());
          linesOfTextRange[linesOfTextRange.length - 1] = linesOfTextRange[linesOfTextRange.length - 1].substring(0, textRange.getEndOffset());
          locationBuilder.setCodeSnippet(Stream.of(linesOfTextRange).collect(joining("\n")));
        }
      } catch (Exception e) {
        LOG.debug("Unable to compute code snippet of '" + fileKey + "' for text range: " + textRange, e);
      }
    }
  }

  private String[] getOrFetchSourceCode(String fileKey, Map<String, String[]> sourceCodeByKey) {
    return sourceCodeByKey.computeIfAbsent(fileKey, k -> {
      try (WsResponse r = wsClient.get("/api/sources/raw?key=" + StringUtils.urlEncode(fileKey))) {
        return r.content().split("\\r?\\n");
      } catch (Exception e) {
        LOG.debug("Unable to fetch source code of '" + fileKey + "'", e);
        return new String[0];
      }
    });
  }

  private static void copyTextRangeFromWs(Location.Builder locationBuilder, Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, TextRange textRange) {
    textRangeBuilder.clear();
    textRangeBuilder.setStartLine(textRange.getStartLine());
    textRangeBuilder.setStartLineOffset(textRange.getStartOffset());
    textRangeBuilder.setEndLine(textRange.getEndLine());
    textRangeBuilder.setEndLineOffset(textRange.getEndOffset());
    locationBuilder.setTextRange(textRangeBuilder);
  }

  private static String getIssuesUrl(String key) {
    // As a small workaround to the 10k limit, we sort on status, descending, in order to have resolved issues first (FP/WF)
    return "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=CODE_SMELL,BUG,VULNERABILITY&s=STATUS&asc=false&componentKeys="
      + StringUtils.urlEncode(key);
  }
}
