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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.Component;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.Issue;

import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

public class IssueApi {

  public static final Version MIN_SQ_VERSION_SUPPORTING_PULL = Version.create("9.6");

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

  private static <T extends Message> List<T> readMessages(InputStream input, Parser<T> parser) {
    List<T> list = new ArrayList<>();
    while (true) {
      T message;
      try {
        message = parser.parseDelimitedFrom(input);
      } catch (InvalidProtocolBufferException e) {
        throw new IllegalStateException("failed to parse protobuf message", e);
      }
      if (message == null) {
        break;
      }
      list.add(message);
    }
    return list;
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

}
