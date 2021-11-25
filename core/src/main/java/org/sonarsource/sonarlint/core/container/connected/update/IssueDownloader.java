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
import javax.annotation.Nullable;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarqube.ws.Common.Flow;
import org.sonarqube.ws.Common.TextRange;
import org.sonarqube.ws.Issues.Component;
import org.sonarqube.ws.Issues.Issue;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Location;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi.DownloadIssuesResult;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;
import org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class IssueDownloader {

  private static final Set<String> NON_CLOSED_STATUSES = new HashSet<>(Arrays.asList("OPEN", "CONFIRMED", "REOPENED"));

  private static final Logger LOG = Loggers.get(IssueDownloader.class);

  private final IssueStorePaths issueStorePaths;
  private final IssueApi issueApi;
  private final SourceApi sourceApi;

  public IssueDownloader(IssueApi issueApi, SourceApi sourceApi, IssueStorePaths issueStorePaths) {
    this.sourceApi = sourceApi;
    this.issueStorePaths = issueStorePaths;
    this.issueApi = issueApi;
  }

  /**
   * Fetch all issues of the component with specified key.
   * If the component doesn't exist or it exists but has no issues, an empty iterator is returned.
   *
   * @param key project key, or file key.
   * @param branchName name of the branch. If null - issues will be downloaded for all branches.
   * @return Iterator of issues. It can be empty but never null.
   */
  public List<Sonarlint.ServerIssue> download(String key, ProjectConfiguration projectConfiguration, boolean fetchTaintVulnerabilities, @Nullable String branchName, ProgressWrapper progress) {
    Sonarlint.ServerIssue.Builder issueBuilder = Sonarlint.ServerIssue.newBuilder();
    Location.Builder locationBuilder = Location.newBuilder();
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder = Sonarlint.ServerIssue.TextRange.newBuilder();
    Sonarlint.ServerIssue.Flow.Builder flowBuilder = Sonarlint.ServerIssue.Flow.newBuilder();

    List<Sonarlint.ServerIssue> result = new ArrayList<>();

    List<ScannerInput.ServerIssue> batchIssues = issueApi.downloadAllFromBatchIssues(key, branchName);

    Set<String> taintRuleKeys = new HashSet<>();
    for (ScannerInput.ServerIssue batchIssue : batchIssues) {
      if (IssueApi.TAINT_REPOS.contains(batchIssue.getRuleRepository())) {
        if (NON_CLOSED_STATUSES.contains(batchIssue.getStatus())) {
          taintRuleKeys.add(new org.sonarsource.sonarlint.core.client.api.common.RuleKey(batchIssue.getRuleRepository(), batchIssue.getRuleKey()).toString());
        }
      } else {
        result.add(toStorageIssue(batchIssue, projectConfiguration, issueBuilder, locationBuilder, textRangeBuilder));
      }
    }

    if (fetchTaintVulnerabilities && !taintRuleKeys.isEmpty()) {
      Map<String, String> sourceCodeByKey = new HashMap<>();
      try {
        DownloadIssuesResult downloadVulnerabilitiesForRules = issueApi.downloadVulnerabilitiesForRules(key, taintRuleKeys, branchName, progress);
        downloadVulnerabilitiesForRules.getIssues()
          .forEach(i -> result.add(
            convertTaintIssue(projectConfiguration, issueBuilder, locationBuilder, textRangeBuilder, flowBuilder, i, downloadVulnerabilitiesForRules.getComponentsByKey(),
              sourceCodeByKey)));
      } catch (Exception e) {
        LOG.warn("Unable to fetch taint vulnerabilities", e);
      }
    }

    return result;
  }

  public Sonarlint.ServerIssue toStorageIssue(ScannerInput.ServerIssue batchIssueFromWs, Sonarlint.ProjectConfiguration projectConfiguration,
    Sonarlint.ServerIssue.Builder issueBuilder, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder) {
    String sqPath = issueStorePaths.fileKeyToSqPath(projectConfiguration, batchIssueFromWs.getModuleKey(), batchIssueFromWs.getPath());

    Location primary = buildPrimaryLocationForBatchIssue(locationBuilder, textRangeBuilder, batchIssueFromWs, sqPath);

    issueBuilder.clear();
    Sonarlint.ServerIssue.Builder builder = issueBuilder
      .setAssigneeLogin(batchIssueFromWs.getAssigneeLogin())
      .setLineHash(batchIssueFromWs.getChecksum())
      .setCreationDate(batchIssueFromWs.getCreationDate())
      .setKey(batchIssueFromWs.getKey())
      .setPrimaryLocation(primary)
      .setResolution(batchIssueFromWs.getResolution())
      .setRuleKey(batchIssueFromWs.getRuleKey())
      .setRuleRepository(batchIssueFromWs.getRuleRepository())
      .setSeverity(batchIssueFromWs.getSeverity().name())
      .setStatus(batchIssueFromWs.getStatus())
      .setType(batchIssueFromWs.getType());

    return builder.build();
  }

  private Location buildPrimaryLocationForBatchIssue(Location.Builder locationBuilder, Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder,
    ScannerInput.ServerIssue issueFromWs, String sqPath) {
    locationBuilder.clear();
    locationBuilder.setPath(sqPath);
    locationBuilder.setMsg(issueFromWs.getMsg());
    if (issueFromWs.hasLine()) {
      textRangeBuilder.clear();
      textRangeBuilder.setStartLine(issueFromWs.getLine());
      locationBuilder.setTextRange(textRangeBuilder);
    }
    return locationBuilder.build();
  }

  private ServerIssue convertTaintIssue(ProjectConfiguration projectConfiguration, Sonarlint.ServerIssue.Builder issueBuilder, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, Sonarlint.ServerIssue.Flow.Builder flowBuilder, Issue issueFromWs,
    Map<String, Component> componentsByKey, Map<String, String> sourceCodeByKey) {
    issueBuilder.clear();
    RuleKey ruleKey = RuleKey.parse(issueFromWs.getRule());
    Location primary = buildPrimaryLocation(projectConfiguration, locationBuilder, textRangeBuilder, issueFromWs, componentsByKey, sourceCodeByKey);
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
      .setStatus(issueFromWs.getStatus())
      .setType(issueFromWs.getType().name());

    buildFlows(projectConfiguration, issueBuilder, locationBuilder, textRangeBuilder, flowBuilder, issueFromWs, componentsByKey, sourceCodeByKey);

    return issueBuilder.build();
  }

  private void buildFlows(ProjectConfiguration projectConfiguration, Sonarlint.ServerIssue.Builder issueBuilder, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, Sonarlint.ServerIssue.Flow.Builder flowBuilder, Issue issueFromWs, Map<String, Component> componentsByKey,
    Map<String, String> sourceCodeByKey) {
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
          setCodeSnippet(locationBuilder, locationFromWs.getComponent(), locationFromWs.getTextRange(), sourceCodeByKey);
        }
        flowBuilder.addLocation(locationBuilder);
      }

      issueBuilder.addFlow(flowBuilder);
    }
  }

  private Location buildPrimaryLocation(ProjectConfiguration projectConfiguration, Location.Builder locationBuilder, Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder,
    Issue issueFromWs, Map<String, Component> componentsByKey, Map<String, String> sourceCodeByKey) {
    locationBuilder.clear();
    locationBuilder.setMsg(issueFromWs.getMessage());
    Component component = componentsByKey.get(issueFromWs.getComponent());
    String sqPath = issueStorePaths.fileKeyToSqPath(projectConfiguration, issueFromWs.getSubProject(), component.getPath());
    locationBuilder.setPath(sqPath);
    if (issueFromWs.hasTextRange()) {
      copyTextRangeFromWs(locationBuilder, textRangeBuilder, issueFromWs.getTextRange());
      setCodeSnippet(locationBuilder, issueFromWs.getComponent(), issueFromWs.getTextRange(), sourceCodeByKey);
    }
    return locationBuilder.build();
  }

  private static void copyTextRangeFromWs(Location.Builder locationBuilder, Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, TextRange textRange) {
    textRangeBuilder.clear();
    textRangeBuilder.setStartLine(textRange.getStartLine());
    textRangeBuilder.setStartLineOffset(textRange.getStartOffset());
    textRangeBuilder.setEndLine(textRange.getEndLine());
    textRangeBuilder.setEndLineOffset(textRange.getEndOffset());
    locationBuilder.setTextRange(textRangeBuilder);
  }

  private void setCodeSnippet(Location.Builder locationBuilder, String fileKey, TextRange textRange, Map<String, String> sourceCodeByKey) {
    String sourceCode = getOrFetchSourceCode(fileKey, sourceCodeByKey);
    if (StringUtils.isEmpty(sourceCode)) {
      return;
    }
    try {
      locationBuilder.setCodeSnippet(ServerApiUtils.extractCodeSnippet(sourceCode, textRange));
    } catch (Exception e) {
      LOG.debug("Unable to compute code snippet of '" + fileKey + "' for text range: " + textRange, e);
    }
  }

  private String getOrFetchSourceCode(String fileKey, Map<String, String> sourceCodeByKey) {
    return sourceCodeByKey.computeIfAbsent(fileKey, k -> sourceApi
      .getRawSourceCode(fileKey)
      .orElse(""));
  }
}
