/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.issue;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.Transition;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedBodyException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.Component;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.Issue;

import static java.util.Objects.requireNonNull;
import static org.sonarsource.sonarlint.core.http.HttpClient.FORM_URL_ENCODED_CONTENT_TYPE;
import static org.sonarsource.sonarlint.core.http.HttpClient.JSON_CONTENT_TYPE;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static org.sonarsource.sonarlint.core.serverapi.util.ProtobufUtil.readMessages;

public class IssueApi {

  public static final Version MIN_SQ_VERSION_SUPPORTING_PULL = Version.create("9.6");

  private static final Map<IssueStatus, Transition> transitionByStatus = Map.of(
    IssueStatus.WONT_FIX, Transition.WONT_FIX,
    IssueStatus.FALSE_POSITIVE, Transition.FALSE_POSITIVE
  );

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper serverApiHelper;

  public IssueApi(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  public static boolean supportIssuePull(boolean isSonarCloud, Version serverVersion) {
    return !isSonarCloud && serverVersion.compareToIgnoreQualifier(IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL) >= 0;
  }

  /**
   * Fetch vulnerabilities of the component with specified key.
   * If the component doesn't exist or it exists but has no issues, an empty iterator is returned.
   *
   * @param key project key, or file key.
   */
  public DownloadIssuesResult downloadVulnerabilitiesForRules(String key, Set<String> ruleKeys, @Nullable String branchName, ProgressMonitor progress) {
    var searchUrl = new StringBuilder();
    searchUrl.append(getVulnerabilitiesUrl(key, ruleKeys));
    searchUrl.append(getUrlBranchParameter(branchName));
    serverApiHelper.getOrganizationKey()
      .ifPresent(org -> searchUrl.append("&organization=").append(UrlUtils.urlEncode(org)));
    List<Issue> result = new ArrayList<>();
    Map<String, String> componentsPathByKey = new HashMap<>();
    serverApiHelper.getPaginated(searchUrl.toString(),
      Issues.SearchWsResponse::parseFrom,
      r -> r.getPaging().getTotal(),
      r -> {
        componentsPathByKey.clear();
        // Ignore project level issues
        componentsPathByKey.putAll(r.getComponentsList().stream().filter(Component::hasPath).collect(Collectors.toMap(Component::getKey, Component::getPath)));
        return r.getIssuesList();
      },
      result::add,
      true,
      progress);

    return new DownloadIssuesResult(result, componentsPathByKey);
  }

  public static class DownloadIssuesResult {
    private final List<Issue> issues;
    private final Map<String, String> componentPathsByKey;

    private DownloadIssuesResult(List<Issue> issues, Map<String, String> componentPathsByKey) {
      this.issues = issues;
      this.componentPathsByKey = componentPathsByKey;
    }

    public List<Issue> getIssues() {
      return issues;
    }

    public Map<String, String> getComponentPathsByKey() {
      return componentPathsByKey;
    }

  }

  private static String getVulnerabilitiesUrl(String key, Set<String> ruleKeys) {
    return "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys="
      + urlEncode(key) + "&rules=" + urlEncode(String.join(",", ruleKeys));
  }

  private static String getUrlBranchParameter(@Nullable String branchName) {
    if (branchName != null) {
      return "&branch=" + urlEncode(branchName);
    }
    return "";
  }

  public List<ScannerInput.ServerIssue> downloadAllFromBatchIssues(String key, @Nullable String branchName) {
    var batchIssueUrl = new StringBuilder();
    batchIssueUrl.append(getBatchIssuesUrl(key));
    batchIssueUrl.append(getUrlBranchParameter(branchName));
    return ServerApiHelper.processTimed(
      () -> serverApiHelper.rawGet(batchIssueUrl.toString()),
      response -> {
        if (response.code() == 403 || response.code() == 404) {
          return Collections.emptyList();
        } else if (response.code() != 200) {
          throw ServerApiHelper.handleError(response);
        }
        var input = response.bodyAsStream();
        var parser = ScannerInput.ServerIssue.parser();
        return readMessages(input, parser);
      },
      duration -> LOG.debug("Downloaded issues in {}ms", duration));
  }

  private static String getBatchIssuesUrl(String key) {
    return "/batch/issues?key=" + UrlUtils.urlEncode(key);
  }

  private static String getPullIssuesUrl(String projectKey, String branchName, Set<Language> enabledLanguages, @Nullable Long changedSince) {
    var enabledLanguageKeys = enabledLanguages.stream().map(Language::getLanguageKey).collect(Collectors.joining(","));
    var url = new StringBuilder()
      .append("/api/issues/pull?projectKey=")
      .append(UrlUtils.urlEncode(projectKey)).append("&branchName=").append(UrlUtils.urlEncode(branchName));
    if (!enabledLanguageKeys.isEmpty()) {
      url.append("&languages=").append(enabledLanguageKeys);
    }
    if (changedSince != null) {
      url.append("&changedSince=").append(changedSince);
    }
    return url.toString();
  }

  public IssuesPullResult pullIssues(String projectKey, String branchName, Set<Language> enabledLanguages, @Nullable Long changedSince) {
    return ServerApiHelper.processTimed(
      () -> serverApiHelper.get(getPullIssuesUrl(projectKey, branchName, enabledLanguages, changedSince)),
      response -> {
        var input = response.bodyAsStream();
        var timestamp = Issues.IssuesPullQueryTimestamp.parseDelimitedFrom(input);
        return new IssuesPullResult(timestamp, readMessages(input, Issues.IssueLite.parser()));
      },
      duration -> LOG.debug("Pulled issues in {}ms", duration));
  }

  public static class IssuesPullResult {
    private final Issues.IssuesPullQueryTimestamp timestamp;
    private final List<Issues.IssueLite> issues;

    public IssuesPullResult(Issues.IssuesPullQueryTimestamp timestamp, List<Issues.IssueLite> issues) {
      this.timestamp = timestamp;
      this.issues = issues;
    }

    public Issues.IssuesPullQueryTimestamp getTimestamp() {
      return timestamp;
    }

    public List<Issues.IssueLite> getIssues() {
      return issues;
    }
  }

  private static String getPullTaintIssuesUrl(String projectKey, String branchName, Set<Language> enabledLanguages, @Nullable Long changedSince) {
    var enabledLanguageKeys = enabledLanguages.stream().map(Language::getLanguageKey).collect(Collectors.joining(","));
    var url = new StringBuilder()
      .append("/api/issues/pull_taint?projectKey=")
      .append(UrlUtils.urlEncode(projectKey)).append("&branchName=").append(UrlUtils.urlEncode(branchName));
    if (!enabledLanguageKeys.isEmpty()) {
      url.append("&languages=").append(enabledLanguageKeys);
    }
    if (changedSince != null) {
      url.append("&changedSince=").append(changedSince);
    }
    return url.toString();
  }

  public TaintIssuesPullResult pullTaintIssues(String projectKey, String branchName, Set<Language> enabledLanguages, @Nullable Long changedSince) {
    return ServerApiHelper.processTimed(
      () -> serverApiHelper.get(getPullTaintIssuesUrl(projectKey, branchName, enabledLanguages, changedSince)),
      response -> {
        var input = response.bodyAsStream();
        var timestamp = Issues.TaintVulnerabilityPullQueryTimestamp.parseDelimitedFrom(input);
        return new TaintIssuesPullResult(timestamp, readMessages(input, Issues.TaintVulnerabilityLite.parser()));
      },
      duration -> LOG.debug("Pulled taint issues in {}ms", duration));
  }

  public CompletableFuture<Void> changeStatusAsync(String issueKey, Transition transition) {
    var body = "issue=" + urlEncode(issueKey) + "&transition=" + urlEncode(transition.getStatus());
    return serverApiHelper.postAsync("/api/issues/do_transition", FORM_URL_ENCODED_CONTENT_TYPE, body)
      .thenAccept(response -> {
        // no data, return void
      });
  }

  public CompletableFuture<Void> addComment(String issueKey, String text) {
    var body = "issue=" + urlEncode(issueKey) + "&text=" + urlEncode(text);
    return serverApiHelper.postAsync("/api/issues/add_comment", FORM_URL_ENCODED_CONTENT_TYPE, body)
      .thenAccept(response -> {
        // no data, return void
      });
  }

  public CompletableFuture<Issue> searchByKey(String issueKey) {
    var searchUrl = new StringBuilder();
    searchUrl.append("/api/issues/search.protobuf?issues=").append(urlEncode(issueKey)).append("&additionalFields=transitions");
    serverApiHelper.getOrganizationKey()
      .ifPresent(org -> searchUrl.append("&organization=").append(UrlUtils.urlEncode(org)));
    searchUrl.append("&ps=1&p=1");
    return serverApiHelper.getAsync(searchUrl.toString())
      .thenApply(rawResponse -> {
        try (var body = rawResponse.bodyAsStream()) {
          var wsResponse = Issues.SearchWsResponse.parseFrom(body);
          if (wsResponse.getIssuesList().isEmpty()) {
            throw new UnexpectedBodyException("No issue found with key '" + issueKey + "'");
          }
          return wsResponse.getIssuesList().get(0);
        } catch (IOException e) {
          LOG.error("Error when searching issue + '" + issueKey + "'", e);
          throw new UnexpectedBodyException(e);
        }
      });
  }

  public CompletableFuture<Void> anticipatedTransitions(String projectKey, List<LocalOnlyIssue> resolvedLocalOnlyIssues) {
    return serverApiHelper.postAsync("/api/issues/anticipated_transitions?projectKey=" + projectKey, JSON_CONTENT_TYPE, new Gson().toJson(adapt(resolvedLocalOnlyIssues)))
      .thenAccept(response -> {
        // no data, return void
      });
  }

  private static List<IssueAnticipatedTransition> adapt(List<LocalOnlyIssue> resolvedLocalOnlyIssues) {
    return resolvedLocalOnlyIssues.stream().map(IssueApi::adapt).collect(Collectors.toList());
  }

  private static IssueAnticipatedTransition adapt(LocalOnlyIssue issue) {
    Integer lineNumber = null;
    String lineHash = null;
    var lineWithHash = issue.getLineWithHash();
    if (lineWithHash != null) {
      lineNumber = lineWithHash.getNumber();
      lineHash = lineWithHash.getHash();
    }
    var resolution = requireNonNull(issue.getResolution());
    return new IssueAnticipatedTransition(issue.getServerRelativePath(), lineNumber, lineHash, issue.getRuleKey(), issue.getMessage(),
      transitionByStatus.get(resolution.getStatus()).getStatus(), resolution.getComment());
  }

  public static class TaintIssuesPullResult {
    private final Issues.TaintVulnerabilityPullQueryTimestamp timestamp;
    private final List<Issues.TaintVulnerabilityLite> issues;

    public TaintIssuesPullResult(Issues.TaintVulnerabilityPullQueryTimestamp timestamp, List<Issues.TaintVulnerabilityLite> issues) {
      this.timestamp = timestamp;
      this.issues = issues;
    }

    public Issues.TaintVulnerabilityPullQueryTimestamp getTimestamp() {
      return timestamp;
    }

    public List<Issues.TaintVulnerabilityLite> getTaintIssues() {
      return issues;
    }
  }



  private static class IssueAnticipatedTransition {
    public final String filePath;
    public final Integer line;
    public final String hash;
    public final String ruleKey;
    public final String issueMessage;
    public final String transition;
    public final String comment;

    private IssueAnticipatedTransition(String filePath, @Nullable Integer line, @Nullable String hash, String ruleKey, String issueMessage, String transition,
      @Nullable String comment) {
      this.filePath = filePath;
      this.line = line;
      this.hash = hash;
      this.ruleKey = ruleKey;
      this.issueMessage = issueMessage;
      this.transition = transition;
      this.comment = comment;
    }
  }
}
