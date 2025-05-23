/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.Flow;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.TextRange;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.Issue;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.TaintVulnerabilityLite;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;
import org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static java.util.function.Predicate.not;
import static org.sonarsource.sonarlint.core.serverconnection.DownloaderUtils.parseProtoImpactSeverity;
import static org.sonarsource.sonarlint.core.serverconnection.DownloaderUtils.parseProtoSoftwareQuality;

public class TaintIssueDownloader {

  private static final Pattern MATCH_ALL_WHITESPACES = Pattern.compile("\\s");

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Set<SonarLanguage> enabledLanguages;

  public TaintIssueDownloader(Set<SonarLanguage> enabledLanguages) {
    this.enabledLanguages = enabledLanguages;
  }

  public List<ServerTaintIssue> downloadTaintFromIssueSearch(ServerApi serverApi, String key, @Nullable String branchName, SonarLintCancelMonitor cancelMonitor) {
    var issueApi = serverApi.issue();

    List<ServerTaintIssue> result = new ArrayList<>();

    Set<String> taintRuleKeys = serverApi.rules().getAllTaintRules(List.of(SonarLanguage.values()), cancelMonitor);
    Map<String, String> sourceCodeByKey = new HashMap<>();
    var downloadVulnerabilitiesForRules = issueApi.downloadVulnerabilitiesForRules(key, taintRuleKeys, branchName, cancelMonitor);
    downloadVulnerabilitiesForRules.getIssues()
      .stream()
      .map(i -> convertTaintVulnerability(serverApi.source(), i, downloadVulnerabilitiesForRules.getComponentPathsByKey(), sourceCodeByKey, cancelMonitor))
      .filter(Objects::nonNull)
      .forEach(result::add);

    return result;
  }

  /**
   * Fetch all taint issues of the project with specified key, using new SQ 9.6 api/issues/pull_taint
   *
   * @param projectKey project key
   * @param branchName name of the branch.
   * @return List of issues. It can be empty but never null.
   */
  public PullTaintResult downloadTaintFromPull(ServerApi serverApi, String projectKey, String branchName, Optional<Instant> lastSync, SonarLintCancelMonitor cancelMonitor) {
    var issueApi = serverApi.issue();

    var apiResult = issueApi.pullTaintIssues(projectKey, branchName, enabledLanguages, lastSync.map(Instant::toEpochMilli).orElse(null), cancelMonitor);
    var changedIssues = apiResult.getTaintIssues()
      .stream()
      // Ignore project level issues
      .filter(i -> i.getMainLocation().hasFilePath())
      .filter(not(TaintVulnerabilityLite::getClosed))
      .map(TaintIssueDownloader::convertLiteTaintIssue)
      .toList();
    var closedIssueKeys = apiResult.getTaintIssues()
      .stream()
      // Ignore project level issues
      .filter(i -> i.getMainLocation().hasFilePath())
      .filter(TaintVulnerabilityLite::getClosed)
      .map(TaintVulnerabilityLite::getKey)
      .collect(Collectors.toSet());

    return new PullTaintResult(Instant.ofEpochMilli(apiResult.getTimestamp().getQueryTimestamp()), changedIssues, closedIssueKeys);
  }

  @CheckForNull
  private static ServerTaintIssue convertTaintVulnerability(SourceApi sourceApi, Issue taintVulnerabilityFromWs,
    Map<String, Path> componentPathsByKey, Map<String, String> sourceCodeByKey, SonarLintCancelMonitor cancelMonitor) {
    var ruleKey = RuleKey.parse(taintVulnerabilityFromWs.getRule());
    var primaryLocation = convertPrimaryLocation(sourceApi, taintVulnerabilityFromWs, componentPathsByKey, sourceCodeByKey, cancelMonitor);
    var filePath = primaryLocation.getFilePath();
    if (filePath == null) {
      // Ignore project level issues
      return null;
    }
    var ruleDescriptionContextKey = taintVulnerabilityFromWs.hasRuleDescriptionContextKey() ? taintVulnerabilityFromWs.getRuleDescriptionContextKey() : null;
    var cleanCodeAttribute = parseProtoCleanCodeAttribute(taintVulnerabilityFromWs);
    var impacts = taintVulnerabilityFromWs.getImpactsList().stream()
      .map(i -> Map.entry(parseProtoSoftwareQuality(i), parseProtoImpactSeverity(i)))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return new ServerTaintIssue(
      UUID.randomUUID(),
      taintVulnerabilityFromWs.getKey(),
      !taintVulnerabilityFromWs.getResolution().isEmpty(),
      ruleKey.toString(),
      primaryLocation.getMessage(),
      filePath,
      ServerApiUtils.parseOffsetDateTime(taintVulnerabilityFromWs.getCreationDate()).toInstant(),
      IssueSeverity.valueOf(taintVulnerabilityFromWs.getSeverity().name()),
      RuleType.valueOf(taintVulnerabilityFromWs.getType().name()),
      primaryLocation.getTextRange(), ruleDescriptionContextKey,
      cleanCodeAttribute, impacts)
      .setFlows(convertFlows(sourceApi, taintVulnerabilityFromWs.getFlowsList(), componentPathsByKey, sourceCodeByKey, cancelMonitor));
  }

  @CheckForNull
  @VisibleForTesting
  static CleanCodeAttribute parseProtoCleanCodeAttribute(Issue taintVulnerabilityFromWs) {
    if (!taintVulnerabilityFromWs.hasCleanCodeAttribute() || taintVulnerabilityFromWs.getCleanCodeAttribute() == Common.CleanCodeAttribute.UNKNOWN_ATTRIBUTE) {
      return null;
    }
    return CleanCodeAttribute.valueOf(taintVulnerabilityFromWs.getCleanCodeAttribute().name());
  }

  @CheckForNull
  @VisibleForTesting
  static CleanCodeAttribute parseProtoCleanCodeAttribute(TaintVulnerabilityLite taintVulnerabilityFromWs) {
    if (!taintVulnerabilityFromWs.hasCleanCodeAttribute() || taintVulnerabilityFromWs.getCleanCodeAttribute() == Common.CleanCodeAttribute.UNKNOWN_ATTRIBUTE) {
      return null;
    }
    return CleanCodeAttribute.valueOf(taintVulnerabilityFromWs.getCleanCodeAttribute().name());
  }

  private static List<ServerTaintIssue.Flow> convertFlows(SourceApi sourceApi, List<Flow> flowsList, Map<String, Path> componentPathsByKey,
    Map<String, String> sourceCodeByKey, SonarLintCancelMonitor cancelMonitor) {
    return flowsList.stream()
      .map(flowFromWs -> new ServerTaintIssue.Flow(flowFromWs.getLocationsList().stream().map(locationFromWs -> {
        var componentPath = componentPathsByKey.get(locationFromWs.getComponent());
        if (locationFromWs.hasTextRange()) {
          var codeSnippet = getCodeSnippet(sourceApi, locationFromWs.getComponent(), locationFromWs.getTextRange(), sourceCodeByKey, cancelMonitor);
          String textRangeHash;
          if (codeSnippet != null) {
            textRangeHash = hash(codeSnippet);
          } else {
            // Use empty String, the client will detect a mismatch with real hash and apply UX for mismatched locations
            textRangeHash = "";
          }
          return new ServerTaintIssue.ServerIssueLocation(componentPath, convertTextRangeFromWs(locationFromWs.getTextRange(), textRangeHash), locationFromWs.getMsg());
        }
        return new ServerTaintIssue.ServerIssueLocation(componentPath, null, locationFromWs.getMsg());
      }).toList()))
      .toList();
  }

  private static TextRangeWithHash toServerTaintIssueTextRange(Issues.TextRange textRange) {
    return new TextRangeWithHash(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset(), textRange.getHash());
  }

  private static ServerTaintIssue convertLiteTaintIssue(TaintVulnerabilityLite liteTaintIssueFromWs) {
    var mainLocation = liteTaintIssueFromWs.getMainLocation();
    // We have filtered out issues without file path earlier
    var filePath = Path.of(mainLocation.getFilePath());
    var creationDate = Instant.ofEpochMilli(liteTaintIssueFromWs.getCreationDate());
    ServerTaintIssue taintIssue;
    var severity = IssueSeverity.valueOf(liteTaintIssueFromWs.getSeverity().name());
    var type = RuleType.valueOf(liteTaintIssueFromWs.getType().name());
    var ruleDescriptionContextKey = liteTaintIssueFromWs.hasRuleDescriptionContextKey() ? liteTaintIssueFromWs.getRuleDescriptionContextKey() : null;
    var cleanCodeAttribute = parseProtoCleanCodeAttribute(liteTaintIssueFromWs);
    var impacts = liteTaintIssueFromWs.getImpactsList().stream()
      .map(i -> Map.entry(
        parseProtoSoftwareQuality(i),
        parseProtoImpactSeverity(i)))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    if (mainLocation.hasTextRange()) {
      taintIssue = new ServerTaintIssue(UUID.randomUUID(), liteTaintIssueFromWs.getKey(), liteTaintIssueFromWs.getResolved(), liteTaintIssueFromWs.getRuleKey(),
        mainLocation.getMessage(),
        filePath, creationDate, severity,
        type, toServerTaintIssueTextRange(mainLocation.getTextRange()), ruleDescriptionContextKey, cleanCodeAttribute, impacts);
    } else {
      taintIssue = new ServerTaintIssue(UUID.randomUUID(), liteTaintIssueFromWs.getKey(), liteTaintIssueFromWs.getResolved(), liteTaintIssueFromWs.getRuleKey(),
        mainLocation.getMessage(),
        filePath, creationDate, severity, type, null, ruleDescriptionContextKey, cleanCodeAttribute, impacts);
    }
    taintIssue.setFlows(liteTaintIssueFromWs.getFlowsList().stream().map(TaintIssueDownloader::convertFlows).toList());
    return taintIssue;
  }

  private static ServerTaintIssue.Flow convertFlows(Issues.Flow flowFromWs) {
    return new ServerTaintIssue.Flow(flowFromWs.getLocationsList().stream().map(locationFromWs -> {
      var filePath = locationFromWs.hasFilePath() ? Path.of(locationFromWs.getFilePath()) : null;
      if (locationFromWs.hasTextRange()) {
        return new ServerTaintIssue.ServerIssueLocation(filePath, toServerTaintIssueTextRange(locationFromWs.getTextRange()), locationFromWs.getMessage());
      } else {
        return new ServerTaintIssue.ServerIssueLocation(filePath, null, locationFromWs.getMessage());
      }
    }).toList());
  }

  private static ServerTaintIssue.ServerIssueLocation convertPrimaryLocation(SourceApi sourceApi, Issue issueFromWs, Map<String, Path> componentPathsByKey,
    Map<String, String> sourceCodeByKey, SonarLintCancelMonitor cancelMonitor) {
    var componentPath = componentPathsByKey.get(issueFromWs.getComponent());
    if (issueFromWs.hasTextRange()) {
      var codeSnippet = getCodeSnippet(sourceApi, issueFromWs.getComponent(), issueFromWs.getTextRange(), sourceCodeByKey, cancelMonitor);
      String textRangeHash;
      if (codeSnippet != null) {
        textRangeHash = hash(codeSnippet);
      } else {
        // Use empty String, the client will detect a mismatch with real hash and apply UX for mismatched locations
        textRangeHash = "";
      }
      return new ServerTaintIssue.ServerIssueLocation(componentPath, convertTextRangeFromWs(issueFromWs.getTextRange(), textRangeHash), issueFromWs.getMessage());
    }
    return new ServerTaintIssue.ServerIssueLocation(componentPath, null, issueFromWs.getMessage());
  }

  static String hash(String codeSnippet) {
    String codeSnippetWithoutWhitespaces = MATCH_ALL_WHITESPACES.matcher(codeSnippet).replaceAll("");
    return DigestUtils.md5Hex(codeSnippetWithoutWhitespaces);
  }

  private static TextRangeWithHash convertTextRangeFromWs(TextRange textRange, String hash) {
    return new TextRangeWithHash(textRange.getStartLine(), textRange.getStartOffset(), textRange.getEndLine(), textRange.getEndOffset(), hash);
  }

  @CheckForNull
  private static String getCodeSnippet(SourceApi sourceApi, String fileKey, TextRange textRange, Map<String, String> sourceCodeByKey, SonarLintCancelMonitor cancelMonitor) {
    var sourceCode = getOrFetchSourceCode(sourceApi, fileKey, sourceCodeByKey, cancelMonitor);
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

  private static String getOrFetchSourceCode(SourceApi sourceApi, String fileKey, Map<String, String> sourceCodeByKey, SonarLintCancelMonitor cancelMonitor) {
    return sourceCodeByKey.computeIfAbsent(fileKey, k -> sourceApi
      .getRawSourceCode(fileKey, cancelMonitor)
      .orElse(""));
  }

  public static class PullTaintResult {
    private final Instant queryTimestamp;
    private final List<ServerTaintIssue> changedIssues;
    private final Set<String> closedIssueKeys;

    public PullTaintResult(Instant queryTimestamp, List<ServerTaintIssue> changedIssues, Set<String> closedIssueKeys) {
      this.queryTimestamp = queryTimestamp;
      this.changedIssues = changedIssues;
      this.closedIssueKeys = closedIssueKeys;
    }

    public Instant getQueryTimestamp() {
      return queryTimestamp;
    }

    public List<ServerTaintIssue> getChangedTaintIssues() {
      return changedIssues;
    }

    public Set<String> getClosedIssueKeys() {
      return closedIssueKeys;
    }
  }
}
