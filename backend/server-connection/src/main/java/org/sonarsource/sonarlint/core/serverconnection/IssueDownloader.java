/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiErrorHandlingWrapper;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.IssueLite;
import org.sonarsource.sonarlint.core.serverapi.rules.RulesApi;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

import static java.util.function.Predicate.not;
import static org.sonarsource.sonarlint.core.serverconnection.DownloaderUtils.parseProtoImpactSeverity;
import static org.sonarsource.sonarlint.core.serverconnection.DownloaderUtils.parseProtoSoftwareQuality;

public class IssueDownloader {

  private final Set<SonarLanguage> enabledLanguages;

  public Set<SonarLanguage> getEnabledLanguages() {
    return enabledLanguages;
  }

  public IssueDownloader(Set<SonarLanguage> enabledLanguages) {
    this.enabledLanguages = enabledLanguages;
  }

  /**
   * Fetch all issues of the component with specified key.
   * If the component doesn't exist or it exists but has no issues, an empty iterator is returned.
   *
   * @param key           project key, or file key.
   * @param branchName    name of the branch.
   * @param cancelMonitor
   * @return List of issues. It can be empty but never null.
   */
  public List<ServerIssue<?>> downloadFromBatch(ServerApiErrorHandlingWrapper serverApiWrapper, String key, @Nullable String branchName, SonarLintCancelMonitor cancelMonitor) {

    var batchIssues = serverApiWrapper.downloadAllFromBatchIssues(key, branchName, cancelMonitor);
    List<ServerIssue<?>> result = new ArrayList<>();

    for (ScannerInput.ServerIssue batchIssue : batchIssues) {
      // We ignore project level issues
      if (!RulesApi.TAINT_REPOS.contains(batchIssue.getRuleRepository()) && batchIssue.hasPath()) {
        result.add(convertBatchIssue(batchIssue));
      }
    }

    return result;
  }

  /**
   * Fetch all issues of the project with specified key, using new SQ 9.6 api/issues/pull
   *
   * @param projectKey project key
   * @param branchName name of the branch.
   * @return List of issues. It can be empty but never null.
   */
  public PullResult downloadFromPull(ServerApiErrorHandlingWrapper serverApiWrapper, String projectKey, String branchName,
    Optional<Instant> lastSync, SonarLintCancelMonitor cancelMonitor) {

    var apiResult = serverApiWrapper.pullIssues(projectKey, branchName, enabledLanguages, lastSync.map(Instant::toEpochMilli).orElse(null), cancelMonitor);
    // Ignore project level issues
    List<ServerIssue<?>> changedIssues = apiResult.getIssues()
      .stream()
      // Ignore project level issues
      .filter(i -> i.getMainLocation().hasFilePath())
      .filter(not(IssueLite::getClosed))
      .map(IssueDownloader::convertLiteIssue)
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

  private static ServerIssue<?> convertBatchIssue(ScannerInput.ServerIssue batchIssueFromWs) {
    var ruleKey = batchIssueFromWs.getRuleRepository() + ":" + batchIssueFromWs.getRuleKey();
    // We have filtered out issues without file path earlier
    var filePath = Path.of(batchIssueFromWs.getPath());
    var creationDate = Instant.ofEpochMilli(batchIssueFromWs.getCreationDate());
    var userSeverity = batchIssueFromWs.getManualSeverity() ? IssueSeverity.valueOf(batchIssueFromWs.getSeverity().name()) : null;
    var ruleType = RuleType.valueOf(batchIssueFromWs.getType());
    var impacts = Collections.<SoftwareQuality, ImpactSeverity>emptyMap();
    if (batchIssueFromWs.hasLine()) {
      return new LineLevelServerIssue(batchIssueFromWs.getKey(), batchIssueFromWs.hasResolution(), ruleKey, batchIssueFromWs.getMsg(), batchIssueFromWs.getChecksum(), filePath,
        creationDate, userSeverity, ruleType, batchIssueFromWs.getLine(), impacts);
    } else {
      return new FileLevelServerIssue(batchIssueFromWs.getKey(), batchIssueFromWs.hasResolution(), ruleKey, batchIssueFromWs.getMsg(), filePath, creationDate, userSeverity,
        ruleType, impacts);
    }
  }

  private static ServerIssue<?> convertLiteIssue(IssueLite liteIssueFromWs) {
    var mainLocation = liteIssueFromWs.getMainLocation();
    // We have filtered out issues without file path earlier
    var filePath = Path.of(mainLocation.getFilePath());
    var creationDate = Instant.ofEpochMilli(liteIssueFromWs.getCreationDate());
    var userSeverity = liteIssueFromWs.hasUserSeverity() ? IssueSeverity.valueOf(liteIssueFromWs.getUserSeverity().name()) : null;
    var ruleType = RuleType.valueOf(liteIssueFromWs.getType().name());
    var impacts = liteIssueFromWs.getImpactsList().stream()
      .map(i -> Map.entry(
        parseProtoSoftwareQuality(i),
        parseProtoImpactSeverity(i)))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    if (mainLocation.hasTextRange()) {
      return new RangeLevelServerIssue(liteIssueFromWs.getKey(), liteIssueFromWs.getResolved(), liteIssueFromWs.getRuleKey(), mainLocation.getMessage(),
        filePath, creationDate, userSeverity,
        ruleType, toServerIssueTextRange(mainLocation.getTextRange()), impacts);
    } else {
      return new FileLevelServerIssue(liteIssueFromWs.getKey(), liteIssueFromWs.getResolved(), liteIssueFromWs.getRuleKey(), mainLocation.getMessage(),
        filePath, creationDate, userSeverity, ruleType, impacts);
    }
  }

  private static TextRangeWithHash toServerIssueTextRange(Issues.TextRange textRange) {
    return new TextRangeWithHash(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset(), textRange.getHash());
  }

  public static class PullResult {
    private final Instant queryTimestamp;
    private final List<ServerIssue<?>> changedIssues;
    private final Set<String> closedIssueKeys;

    public PullResult(Instant queryTimestamp, List<ServerIssue<?>> changedIssues, Set<String> closedIssueKeys) {
      this.queryTimestamp = queryTimestamp;
      this.changedIssues = changedIssues;
      this.closedIssueKeys = closedIssueKeys;
    }

    public Instant getQueryTimestamp() {
      return queryTimestamp;
    }

    public List<ServerIssue<?>> getChangedIssues() {
      return changedIssues;
    }

    public Set<String> getClosedIssueKeys() {
      return closedIssueKeys;
    }
  }

}
