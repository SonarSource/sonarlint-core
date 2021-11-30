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
package org.sonarsource.sonarlint.core.serverapi.issue;

import com.google.protobuf.Parser;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.Issues.Component;
import org.sonarqube.ws.Issues.Issue;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static java.util.stream.Collectors.joining;
import static org.sonarsource.sonarlint.core.util.StringUtils.urlEncode;

public class IssueApi {

  private static final Logger LOG = Loggers.get(IssueApi.class);

  public static final Set<String> TAINT_REPOS = new HashSet<>(
    Arrays.asList("roslyn.sonaranalyzer.security.cs", "javasecurity", "jssecurity", "tssecurity", "phpsecurity", "pythonsecurity"));

  private final ServerApiHelper serverApiHelper;

  public IssueApi(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  /**
   * Fetch vulnerabilities of the component with specified key.
   * If the component doesn't exist or it exists but has no issues, an empty iterator is returned.
   *
   * @param key project key, or file key.
   */
  public DownloadIssuesResult downloadVulnerabilitiesForRules(String key, Set<String> ruleKeys, @Nullable String branchName, ProgressWrapper progress) {
    StringBuilder searchUrl = new StringBuilder();
    searchUrl.append(getVulnerabilitiesUrl(key, ruleKeys));
    if (branchName != null) {
      searchUrl.append(getVulnerabilitiesUrlBranchParameter(branchName));
    }
    serverApiHelper.getOrganizationKey()
      .ifPresent(org -> searchUrl.append("&organization=").append(StringUtils.urlEncode(org)));
    List<Issue> result = new ArrayList<>();
    Map<String, Component> componentsByKey = new HashMap<>();
    serverApiHelper.getPaginated(searchUrl.toString(),
      Issues.SearchWsResponse::parseFrom,
      Issues.SearchWsResponse::getPaging,
      r -> {
        componentsByKey.clear();
        componentsByKey.putAll(r.getComponentsList().stream().collect(Collectors.toMap(Component::getKey, c -> c)));
        return r.getIssuesList();
      },
      result::add,
      true,
      progress);

    return new DownloadIssuesResult(result, componentsByKey);
  }

  public static class DownloadIssuesResult {
    private final List<Issue> issues;
    private final Map<String, Component> componentsByKey;

    private DownloadIssuesResult(List<Issue> issues, Map<String, Component> componentsByKey) {
      this.issues = issues;
      this.componentsByKey = componentsByKey;
    }

    public List<Issue> getIssues() {
      return issues;
    }

    public Map<String, Component> getComponentsByKey() {
      return componentsByKey;
    }

  }

  private static String getVulnerabilitiesUrl(String key, Set<String> ruleKeys) {
    return "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys="
      + urlEncode(key) + "&rules=" + urlEncode(String.join(",", ruleKeys));
  }

  private static String getVulnerabilitiesUrlBranchParameter(String branchName) {
    return "&branch=" + urlEncode(branchName);
  }

  public List<ScannerInput.ServerIssue> downloadAllFromBatchIssues(String key, @Nullable String branchName) {
    StringBuilder batchIssueUrl = new StringBuilder();
    batchIssueUrl.append(getBatchIssuesUrl(key));
    if (branchName != null) {
      batchIssueUrl.append("&branch=").append(branchName);
    }
    return ServerApiHelper.processTimed(
      () -> serverApiHelper.rawGet(batchIssueUrl.toString()),
      response -> {
        if (response.code() == 403 || response.code() == 404) {
          return Collections.emptyList();
        } else if (response.code() != 200) {
          throw ServerApiHelper.handleError(response);
        }
        InputStream input = response.bodyAsStream();
        Parser<ScannerInput.ServerIssue> parser = ScannerInput.ServerIssue.parser();
        return ProtobufUtil.readMessages(input, parser);
      },
      duration -> LOG.debug("Downloaded issues in {}ms", duration));
  }

  private static String getBatchIssuesUrl(String key) {
    return "/batch/issues?key=" + StringUtils.urlEncode(key);
  }

}
