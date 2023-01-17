/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.IssueLite;
import org.sonarsource.sonarlint.core.serverapi.rules.RulesApi;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

import static java.util.function.Predicate.not;

public class IssueDownloader {

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
   * Fetch all issues of the project with specified key, using new SQ 9.6 api/issues/pull
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

  private static ServerIssue convertBatchIssue(ScannerInput.ServerIssue batchIssueFromWs) {
    var ruleKey = batchIssueFromWs.getRuleRepository() + ":" + batchIssueFromWs.getRuleKey();
    // We have filtered out issues without file path earlier
    var filePath = batchIssueFromWs.getPath();
    var creationDate = Instant.ofEpochMilli(batchIssueFromWs.getCreationDate());
    var userSeverity = batchIssueFromWs.getManualSeverity() ? IssueSeverity.valueOf(batchIssueFromWs.getSeverity().name()) : null;
    var ruleType = RuleType.valueOf(batchIssueFromWs.getType());
    if (batchIssueFromWs.hasLine()) {
      return new LineLevelServerIssue(batchIssueFromWs.getKey(), batchIssueFromWs.hasResolution(), ruleKey, batchIssueFromWs.getMsg(), batchIssueFromWs.getChecksum(), filePath,
        creationDate, userSeverity, ruleType, batchIssueFromWs.getLine());
    } else {
      return new FileLevelServerIssue(batchIssueFromWs.getKey(), batchIssueFromWs.hasResolution(), ruleKey, batchIssueFromWs.getMsg(), filePath, creationDate, userSeverity,
        ruleType);
    }
  }

  private static ServerIssue convertLiteIssue(IssueLite liteIssueFromWs) {
    var mainLocation = liteIssueFromWs.getMainLocation();
    // We have filtered out issues without file path earlier
    var filePath = mainLocation.getFilePath();
    var creationDate = Instant.ofEpochMilli(liteIssueFromWs.getCreationDate());
    var userSeverity = liteIssueFromWs.hasUserSeverity() ? IssueSeverity.valueOf(liteIssueFromWs.getUserSeverity().name()) : null;
    var ruleType = RuleType.valueOf(liteIssueFromWs.getType().name());
    if (mainLocation.hasTextRange()) {
      return new RangeLevelServerIssue(liteIssueFromWs.getKey(), liteIssueFromWs.getResolved(), liteIssueFromWs.getRuleKey(), mainLocation.getMessage(),
        filePath, creationDate, userSeverity,
        ruleType, toServerIssueTextRange(mainLocation.getTextRange()));
    } else {
      return new FileLevelServerIssue(liteIssueFromWs.getKey(), liteIssueFromWs.getResolved(), liteIssueFromWs.getRuleKey(), mainLocation.getMessage(),
        filePath, creationDate, userSeverity, ruleType);
    }
  }

  private static TextRangeWithHash toServerIssueTextRange(Issues.TextRange textRange) {
    return new TextRangeWithHash(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset(), textRange.getHash());
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
