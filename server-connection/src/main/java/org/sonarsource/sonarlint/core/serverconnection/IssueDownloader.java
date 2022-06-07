/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.Flow;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.TextRange;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.Issue;
import org.sonarsource.sonarlint.core.serverapi.rules.RulesApi;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;
import org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils;

public class IssueDownloader {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  /**
   * Fetch all issues of the component with specified key.
   * If the component doesn't exist or it exists but has no issues, an empty iterator is returned.
   *
   * @param key project key, or file key.
   * @param branchName name of the branch. If null - issues will be downloaded only for the main branch.
   * @return Iterator of issues. It can be empty but never null.
   */
  public List<ServerIssue> download(ServerApiHelper serverApiHelper, String key, @Nullable String branchName, ProgressMonitor progress) {
    var serverApi = new ServerApi(serverApiHelper);
    var issueApi = serverApi.issue();

    List<ServerIssue> result = new ArrayList<>();

    var batchIssues = issueApi.downloadAllFromBatchIssues(key, branchName);

    for (ScannerInput.ServerIssue batchIssue : batchIssues) {
      if (!RulesApi.TAINT_REPOS.contains(batchIssue.getRuleRepository())) {
        result.add(convertClassicIssue(batchIssue));
      }
    }

    return result;
  }

  public List<ServerIssue> downloadTaint(ServerApiHelper serverApiHelper, String key, @Nullable String branchName, ProgressMonitor progress) {
    var serverApi = new ServerApi(serverApiHelper);
    var issueApi = serverApi.issue();

    List<ServerIssue> result = new ArrayList<>();

    Set<String> taintRuleKeys = serverApi.rules().getAllTaintRules(List.of(Language.values()), progress);
    Map<String, String> sourceCodeByKey = new HashMap<>();
    try {
      var downloadVulnerabilitiesForRules = issueApi.downloadVulnerabilitiesForRules(key, taintRuleKeys, branchName, progress);
      downloadVulnerabilitiesForRules.getIssues()
        .forEach(i -> result.add(
          convertTaintVulnerability(new ServerApi(serverApiHelper).source(), i, downloadVulnerabilitiesForRules.getComponentPathsByKey(), sourceCodeByKey)));
    } catch (Exception e) {
      LOG.warn("Unable to fetch taint vulnerabilities", e);
    }

    return result;
  }

  public ServerIssue convertClassicIssue(ScannerInput.ServerIssue batchIssueFromWs) {
    var primaryLocation = convertPrimaryLocationForBatchIssue(batchIssueFromWs, batchIssueFromWs.getPath());
    return new ServerIssue(
      batchIssueFromWs.getKey(),
      !batchIssueFromWs.getResolution().isEmpty(),
      batchIssueFromWs.getRuleRepository() + ":" + batchIssueFromWs.getRuleKey(),
      primaryLocation.getMessage(),
      batchIssueFromWs.getChecksum(),
      primaryLocation.getFilePath(),
      Instant.ofEpochMilli(batchIssueFromWs.getCreationDate()),
      batchIssueFromWs.getSeverity().name(),
      batchIssueFromWs.getType(),
      primaryLocation.getTextRange());
  }

  private static ServerIssue.ServerIssueLocation convertPrimaryLocationForBatchIssue(ScannerInput.ServerIssue issueFromWs, String sqPath) {
    return new ServerIssue.ServerIssueLocation(sqPath, new ServerIssue.TextRange(issueFromWs.getLine()), issueFromWs.getMsg(), null);
  }

  private static ServerIssue convertTaintVulnerability(SourceApi sourceApi, Issue taintVulnerabilityFromWs,
    Map<String, String> componentsByKey, Map<String, String> sourceCodeByKey) {
    var ruleKey = RuleKey.parse(taintVulnerabilityFromWs.getRule());
    var primaryLocation = convertPrimaryLocation(sourceApi, taintVulnerabilityFromWs, componentsByKey, sourceCodeByKey);
    return new ServerIssue(
      taintVulnerabilityFromWs.getKey(),
      !taintVulnerabilityFromWs.getResolution().isEmpty(),
      ruleKey.toString(),
      primaryLocation.getMessage(),
      taintVulnerabilityFromWs.getHash(),
      primaryLocation.getFilePath(),
      ServerApiUtils.parseOffsetDateTime(taintVulnerabilityFromWs.getCreationDate()).toInstant(),
      taintVulnerabilityFromWs.getSeverity().name(),
      taintVulnerabilityFromWs.getType().name(),
      primaryLocation.getTextRange())
        .setFlows(convertFlows(sourceApi, taintVulnerabilityFromWs.getFlowsList(), componentsByKey, sourceCodeByKey))
        .setCodeSnippet(primaryLocation.getCodeSnippet());
  }

  private static List<ServerIssue.Flow> convertFlows(SourceApi sourceApi, List<Flow> flowsList, Map<String, String> componentPathsByKey,
    Map<String, String> sourceCodeByKey) {
    return flowsList.stream()
      .map(flowFromWs -> new ServerIssue.Flow(flowFromWs.getLocationsList().stream().map(locationFromWs -> {
        var componentPath = componentPathsByKey.get(locationFromWs.getComponent());
        var textRange = locationFromWs.hasTextRange() ? convertTextRangeFromWs(locationFromWs.getTextRange()) : null;
        var codeSnippet = locationFromWs.hasTextRange() ? getCodeSnippet(sourceApi, locationFromWs.getComponent(), locationFromWs.getTextRange(), sourceCodeByKey) : null;
        return new ServerIssue.ServerIssueLocation(componentPath, textRange, locationFromWs.getMsg(), codeSnippet);
      }).collect(Collectors.toList())))
      .collect(Collectors.toList());
  }

  private static ServerIssue.ServerIssueLocation convertPrimaryLocation(SourceApi sourceApi, Issue issueFromWs, Map<String, String> componentPathsByKey,
    Map<String, String> sourceCodeByKey) {
    var componentPath = componentPathsByKey.get(issueFromWs.getComponent());
    var textRange = issueFromWs.hasTextRange() ? convertTextRangeFromWs(issueFromWs.getTextRange()) : null;
    var codeSnippet = issueFromWs.hasTextRange() ? getCodeSnippet(sourceApi, issueFromWs.getComponent(), issueFromWs.getTextRange(), sourceCodeByKey) : null;
    return new ServerIssue.ServerIssueLocation(componentPath, textRange, issueFromWs.getMessage(), codeSnippet);
  }

  private static ServerIssue.TextRange convertTextRangeFromWs(TextRange textRange) {
    return new ServerIssue.TextRange(textRange.getStartLine(), textRange.getStartOffset(), textRange.getEndLine(), textRange.getEndOffset());
  }

  @CheckForNull
  private static String getCodeSnippet(SourceApi sourceApi, String fileKey, TextRange textRange, Map<String, String> sourceCodeByKey) {
    var sourceCode = getOrFetchSourceCode(sourceApi, fileKey, sourceCodeByKey);
    if (StringUtils.isEmpty(sourceCode)) {
      return null;
    }
    try {
      return ServerApiUtils.extractCodeSnippet(sourceCode, textRange);
    } catch (Exception e) {
      LOG.debug("Unable to compute code snippet of '" + fileKey + "' for text range: " + textRange, e);
    }
    return null;
  }

  private static String getOrFetchSourceCode(SourceApi sourceApi, String fileKey, Map<String, String> sourceCodeByKey) {
    return sourceCodeByKey.computeIfAbsent(fileKey, k -> sourceApi
      .getRawSourceCode(fileKey)
      .orElse(""));
  }
}
