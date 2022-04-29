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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.rule.RuleKey;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarqube.ws.Common.Flow;
import org.sonarqube.ws.Common.TextRange;
import org.sonarqube.ws.Issues.Issue;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Location;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;
import org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils;

public class IssueDownloader {

  private static final Set<String> NON_CLOSED_STATUSES = new HashSet<>(Arrays.asList("OPEN", "CONFIRMED", "REOPENED"));

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final IssueStorePaths issueStorePaths;

  public IssueDownloader(IssueStorePaths issueStorePaths) {
    this.issueStorePaths = issueStorePaths;
  }

  /**
   * Fetch all issues of the component with specified key.
   * If the component doesn't exist or it exists but has no issues, an empty iterator is returned.
   *
   * @param key project key, or file key.
   * @param branchName name of the branch. If null - issues will be downloaded only for the main branch.
   * @return Iterator of issues. It can be empty but never null.
   */
  public List<Sonarlint.ServerIssue> download(ServerApiHelper serverApiHelper, String key, boolean fetchTaintVulnerabilities, @Nullable String branchName,
    ProgressMonitor progress) {
    var issueApi = new ServerApi(serverApiHelper).issue();
    var issueBuilder = Sonarlint.ServerIssue.newBuilder();
    var locationBuilder = Location.newBuilder();
    var textRangeBuilder = Sonarlint.ServerIssue.TextRange.newBuilder();
    var flowBuilder = Sonarlint.ServerIssue.Flow.newBuilder();

    List<Sonarlint.ServerIssue> result = new ArrayList<>();

    var batchIssues = issueApi.downloadAllFromBatchIssues(key, branchName);

    Set<String> taintRuleKeys = new HashSet<>();
    for (ScannerInput.ServerIssue batchIssue : batchIssues) {
      if (IssueApi.TAINT_REPOS.contains(batchIssue.getRuleRepository())) {
        if (NON_CLOSED_STATUSES.contains(batchIssue.getStatus())) {
          taintRuleKeys.add(new org.sonarsource.sonarlint.core.client.api.common.RuleKey(batchIssue.getRuleRepository(), batchIssue.getRuleKey()).toString());
        }
      } else {
        result.add(toStorageIssue(batchIssue, issueBuilder, locationBuilder, textRangeBuilder));
      }
    }

    if (fetchTaintVulnerabilities && !taintRuleKeys.isEmpty()) {
      Map<String, String> sourceCodeByKey = new HashMap<>();
      try {
        var downloadVulnerabilitiesForRules = issueApi.downloadVulnerabilitiesForRules(key, taintRuleKeys, branchName, progress);
        downloadVulnerabilitiesForRules.getIssues()
          .forEach(i -> result.add(
            convertTaintIssue(new ServerApi(serverApiHelper).source(), issueBuilder, locationBuilder, textRangeBuilder, flowBuilder, i,
              downloadVulnerabilitiesForRules.getComponentPathsByKey(), sourceCodeByKey)));
      } catch (Exception e) {
        LOG.warn("Unable to fetch taint vulnerabilities", e);
      }
    }

    return result;
  }

  public Sonarlint.ServerIssue toStorageIssue(ScannerInput.ServerIssue batchIssueFromWs, Sonarlint.ServerIssue.Builder issueBuilder, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder) {
    var sqPath = batchIssueFromWs.getPath();

    var primary = buildPrimaryLocationForBatchIssue(locationBuilder, textRangeBuilder, batchIssueFromWs, sqPath);

    issueBuilder.clear();
    var builder = issueBuilder
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

  private static Location buildPrimaryLocationForBatchIssue(Location.Builder locationBuilder, Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder,
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

  private static ServerIssue convertTaintIssue(SourceApi sourceApi, Sonarlint.ServerIssue.Builder issueBuilder,
    Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, Sonarlint.ServerIssue.Flow.Builder flowBuilder, Issue issueFromWs,
    Map<String, String> componentsByKey, Map<String, String> sourceCodeByKey) {
    issueBuilder.clear();
    var ruleKey = RuleKey.parse(issueFromWs.getRule());
    var primary = buildPrimaryLocation(sourceApi, locationBuilder, textRangeBuilder, issueFromWs, componentsByKey, sourceCodeByKey);
    issueBuilder
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

    buildFlows(sourceApi, issueBuilder, locationBuilder, textRangeBuilder, flowBuilder, issueFromWs, componentsByKey, sourceCodeByKey);

    return issueBuilder.build();
  }

  private static void buildFlows(SourceApi sourceApi, Sonarlint.ServerIssue.Builder issueBuilder, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder, Sonarlint.ServerIssue.Flow.Builder flowBuilder, Issue issueFromWs, Map<String, String> componentPathsByKey,
    Map<String, String> sourceCodeByKey) {
    for (Flow flowFromWs : issueFromWs.getFlowsList()) {
      flowBuilder.clear();

      for (org.sonarqube.ws.Common.Location locationFromWs : flowFromWs.getLocationsList()) {
        locationBuilder.clear();
        locationBuilder.setMsg(locationFromWs.getMsg());
        var componentPath = componentPathsByKey.get(locationFromWs.getComponent());
        locationBuilder.setPath(componentPath);
        if (locationFromWs.hasTextRange()) {
          copyTextRangeFromWs(locationBuilder, textRangeBuilder, locationFromWs.getTextRange());
          setCodeSnippet(sourceApi, locationBuilder, locationFromWs.getComponent(), locationFromWs.getTextRange(), sourceCodeByKey);
        }
        flowBuilder.addLocation(locationBuilder);
      }

      issueBuilder.addFlow(flowBuilder);
    }
  }

  private static Location buildPrimaryLocation(SourceApi sourceApi, Location.Builder locationBuilder,
    Sonarlint.ServerIssue.TextRange.Builder textRangeBuilder,
    Issue issueFromWs, Map<String, String> componentPathsByKey, Map<String, String> sourceCodeByKey) {
    locationBuilder.clear();
    locationBuilder.setMsg(issueFromWs.getMessage());
    var componentPath = componentPathsByKey.get(issueFromWs.getComponent());
    locationBuilder.setPath(componentPath);
    if (issueFromWs.hasTextRange()) {
      copyTextRangeFromWs(locationBuilder, textRangeBuilder, issueFromWs.getTextRange());
      setCodeSnippet(sourceApi, locationBuilder, issueFromWs.getComponent(), issueFromWs.getTextRange(), sourceCodeByKey);
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

  private static void setCodeSnippet(SourceApi sourceApi, Location.Builder locationBuilder, String fileKey, TextRange textRange, Map<String, String> sourceCodeByKey) {
    var sourceCode = getOrFetchSourceCode(sourceApi, fileKey, sourceCodeByKey);
    if (StringUtils.isEmpty(sourceCode)) {
      return;
    }
    try {
      locationBuilder.setCodeSnippet(ServerApiUtils.extractCodeSnippet(sourceCode, textRange));
    } catch (Exception e) {
      LOG.debug("Unable to compute code snippet of '" + fileKey + "' for text range: " + textRange, e);
    }
  }

  private static String getOrFetchSourceCode(SourceApi sourceApi, String fileKey, Map<String, String> sourceCodeByKey) {
    return sourceCodeByKey.computeIfAbsent(fileKey, k -> sourceApi
      .getRawSourceCode(fileKey)
      .orElse(""));
  }
}
