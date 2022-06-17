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
import java.util.Objects;
import java.util.Optional;
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
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.Flow;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.TextRange;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.Issue;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.IssueLite;
import org.sonarsource.sonarlint.core.serverapi.rules.RulesApi;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;
import org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

import static java.util.function.Predicate.not;

public class IssueDownloader {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Set<Language> enabledLanguages;

  public IssueDownloader(Set<Language> enabledLanguages) {
    this.enabledLanguages = enabledLanguages;
  }

  /**
   * Fetch all issues of the component with specified key.
   * If the component doesn't exist or it exists but has no issues, an empty iterator is returned.
   *
   * @param key project key, or file key.
   * @param branchName name of the branch.
   * @return List of issues. It can be empty but never null.
   */
  public List<ServerIssue> downloadFromBatch(ServerApi serverApi, String key, @Nullable String branchName) {
    var issueApi = serverApi.issue();

    List<ServerIssue> result = new ArrayList<>();

    var batchIssues = issueApi.downloadAllFromBatchIssues(key, branchName);

    for (ScannerInput.ServerIssue batchIssue : batchIssues) {
      // We ignore project level issues
      if (!RulesApi.TAINT_REPOS.contains(batchIssue.getRuleRepository()) && batchIssue.hasPath()) {
        result.add(convertBatchIssue(batchIssue));
      }
    }

    return result;
  }

  /**
   * Fetch all issues of the project with specified key, using new SQ 9.5 api/issues/pull
   *
   * @param projectKey project key
   * @param branchName name of the branch.
   * @return List of issues. It can be empty but never null.
   */
  public PullResult downloadFromPull(ServerApi serverApi, String projectKey, String branchName, Optional<Instant> lastSync) {
    var issueApi = serverApi.issue();

    var apiResult = issueApi.pullIssues(projectKey, branchName, enabledLanguages, lastSync.map(Instant::toEpochMilli).orElse(null));
    // Ignore project level issues
    var changedIssues = apiResult.getIssues()
      .stream()
      // Ignore project level issues
      .filter(i -> i.getMainLocation().hasFilePath())
      .filter(not(IssueLite::getClosed))
      .map(this::convertLiteIssue)
      .collect(Collectors.toList());
    var closedIssueKeys = apiResult.getIssues()
      .stream()
      // Ignore project level issues
      .filter(i -> i.getMainLocation().hasFilePath())
      .filter(IssueLite::getClosed)
      .map(IssueLite::getKey)
      .collect(Collectors.toSet());

    return new PullResult(Instant.ofEpochMilli(apiResult.getTimestamp().getQueryTimestamp()), changedIssues, closedIssueKeys);
  }

  public List<ServerTaintIssue> downloadTaint(ServerApi serverApi, String key, @Nullable String branchName, ProgressMonitor progress) {
    var issueApi = serverApi.issue();

    List<ServerTaintIssue> result = new ArrayList<>();

    Set<String> taintRuleKeys = serverApi.rules().getAllTaintRules(List.of(Language.values()), progress);
    Map<String, String> sourceCodeByKey = new HashMap<>();
    try {
      var downloadVulnerabilitiesForRules = issueApi.downloadVulnerabilitiesForRules(key, taintRuleKeys, branchName, progress);
      downloadVulnerabilitiesForRules.getIssues()
        .stream()
        .map(i -> convertTaintVulnerability(serverApi.source(), i, downloadVulnerabilitiesForRules.getComponentPathsByKey(), sourceCodeByKey))
        .filter(Objects::nonNull)
        .forEach(result::add);
    } catch (Exception e) {
      LOG.warn("Unable to fetch taint vulnerabilities", e);
    }

    return result;
  }

  public ServerIssue convertBatchIssue(ScannerInput.ServerIssue batchIssueFromWs) {
    var ruleKey = batchIssueFromWs.getRuleRepository() + ":" + batchIssueFromWs.getRuleKey();
    // We have filtered out issues without file path earlier
    var filePath = batchIssueFromWs.getPath();
    var creationDate = Instant.ofEpochMilli(batchIssueFromWs.getCreationDate());
    var userSeverity = batchIssueFromWs.getManualSeverity() ? batchIssueFromWs.getSeverity().name() : null;
    if (batchIssueFromWs.hasLine()) {
      return new LineLevelServerIssue(batchIssueFromWs.getKey(), batchIssueFromWs.hasResolution(), ruleKey, batchIssueFromWs.getMsg(), batchIssueFromWs.getChecksum(), filePath,
        creationDate, userSeverity, batchIssueFromWs.getType(), batchIssueFromWs.getLine());
    } else {
      return new FileLevelServerIssue(batchIssueFromWs.getKey(), batchIssueFromWs.hasResolution(), ruleKey, batchIssueFromWs.getMsg(), filePath, creationDate, userSeverity,
        batchIssueFromWs.getType());
    }
  }

  public ServerIssue convertLiteIssue(IssueLite liteIssueFromWs) {
    var mainLocation = liteIssueFromWs.getMainLocation();
    // We have filtered out issues without file path earlier
    var filePath = mainLocation.getFilePath();
    var creationDate = Instant.ofEpochMilli(liteIssueFromWs.getCreationDate());
    var userSeverity = liteIssueFromWs.hasUserSeverity() ? liteIssueFromWs.getUserSeverity() : null;
    if (mainLocation.hasTextRange()) {
      return new RangeLevelServerIssue(liteIssueFromWs.getKey(), liteIssueFromWs.getResolved(), liteIssueFromWs.getRuleKey(), mainLocation.getMessage(),
        mainLocation.getTextRange().getHash(), filePath, creationDate, userSeverity,
        liteIssueFromWs.getType(), toServerIssueTextRange(mainLocation.getTextRange()));
    } else {
      return new FileLevelServerIssue(liteIssueFromWs.getKey(), liteIssueFromWs.getResolved(), liteIssueFromWs.getRuleKey(), mainLocation.getMessage(),
        filePath, creationDate, userSeverity, liteIssueFromWs.getType());
    }
  }

  private static RangeLevelServerIssue.TextRange toServerIssueTextRange(Issues.TextRange textRange) {
    return new RangeLevelServerIssue.TextRange(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }

  @CheckForNull
  private static ServerTaintIssue convertTaintVulnerability(SourceApi sourceApi, Issue taintVulnerabilityFromWs,
    Map<String, String> componentsByKey, Map<String, String> sourceCodeByKey) {
    var ruleKey = RuleKey.parse(taintVulnerabilityFromWs.getRule());
    var primaryLocation = convertPrimaryLocation(sourceApi, taintVulnerabilityFromWs, componentsByKey, sourceCodeByKey);
    String filePath = primaryLocation.getFilePath();
    if (filePath == null) {
      // Ignore project level issues
      return null;
    }
    return new ServerTaintIssue(
      taintVulnerabilityFromWs.getKey(),
      !taintVulnerabilityFromWs.getResolution().isEmpty(),
      ruleKey.toString(),
      primaryLocation.getMessage(),
      taintVulnerabilityFromWs.getHash(),
      filePath,
      ServerApiUtils.parseOffsetDateTime(taintVulnerabilityFromWs.getCreationDate()).toInstant(),
      taintVulnerabilityFromWs.getSeverity().name(),
      taintVulnerabilityFromWs.getType().name(),
      primaryLocation.getTextRange())
        .setFlows(convertFlows(sourceApi, taintVulnerabilityFromWs.getFlowsList(), componentsByKey, sourceCodeByKey))
        .setCodeSnippet(primaryLocation.getCodeSnippet());
  }

  private static List<ServerTaintIssue.Flow> convertFlows(SourceApi sourceApi, List<Flow> flowsList, Map<String, String> componentPathsByKey,
    Map<String, String> sourceCodeByKey) {
    return flowsList.stream()
      .map(flowFromWs -> new ServerTaintIssue.Flow(flowFromWs.getLocationsList().stream().map(locationFromWs -> {
        var componentPath = componentPathsByKey.get(locationFromWs.getComponent());
        var textRange = locationFromWs.hasTextRange() ? convertTextRangeFromWs(locationFromWs.getTextRange()) : null;
        var codeSnippet = locationFromWs.hasTextRange() ? getCodeSnippet(sourceApi, locationFromWs.getComponent(), locationFromWs.getTextRange(), sourceCodeByKey) : null;
        return new ServerTaintIssue.ServerIssueLocation(componentPath, textRange, locationFromWs.getMsg(), codeSnippet);
      }).collect(Collectors.toList())))
      .collect(Collectors.toList());
  }

  private static ServerTaintIssue.ServerIssueLocation convertPrimaryLocation(SourceApi sourceApi, Issue issueFromWs, Map<String, String> componentPathsByKey,
    Map<String, String> sourceCodeByKey) {
    var componentPath = componentPathsByKey.get(issueFromWs.getComponent());
    var textRange = issueFromWs.hasTextRange() ? convertTextRangeFromWs(issueFromWs.getTextRange()) : null;
    var codeSnippet = issueFromWs.hasTextRange() ? getCodeSnippet(sourceApi, issueFromWs.getComponent(), issueFromWs.getTextRange(), sourceCodeByKey) : null;
    return new ServerTaintIssue.ServerIssueLocation(componentPath, textRange, issueFromWs.getMessage(), codeSnippet);
  }

  private static ServerTaintIssue.TextRange convertTextRangeFromWs(TextRange textRange) {
    return new ServerTaintIssue.TextRange(textRange.getStartLine(), textRange.getStartOffset(), textRange.getEndLine(), textRange.getEndOffset());
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

  public static class PullResult {
    private final Instant queryTimestamp;
    private final List<ServerIssue> changedIssues;
    private final Set<String> closedIssueKeys;

    public PullResult(Instant queryTimestamp, List<ServerIssue> changedIssues, Set<String> closedIssueKeys) {
      this.queryTimestamp = queryTimestamp;
      this.changedIssues = changedIssues;
      this.closedIssueKeys = closedIssueKeys;
    }

    public Instant getQueryTimestamp() {
      return queryTimestamp;
    }

    public List<ServerIssue> getChangedIssues() {
      return changedIssues;
    }

    public Set<String> getClosedIssueKeys() {
      return closedIssueKeys;
    }
  }
}
