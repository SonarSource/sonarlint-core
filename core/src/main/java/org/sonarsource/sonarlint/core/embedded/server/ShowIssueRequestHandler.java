/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.FlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.LocationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.rules.RulesApi;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Named
@Singleton
public class ShowIssueRequestHandler implements HttpRequestHandler {

  private final SonarLintRpcClient client;
  private final ServerApiProvider serverApiProvider;
  private final TelemetryService telemetryService;
  private final RequestHandlerBindingAssistant requestHandlerBindingAssistant;

  public ShowIssueRequestHandler(SonarLintRpcClient client, ServerApiProvider serverApiProvider, TelemetryService telemetryService,
    RequestHandlerBindingAssistant requestHandlerBindingAssistant) {
    this.client = client;
    this.serverApiProvider = serverApiProvider;
    this.telemetryService = telemetryService;
    this.requestHandlerBindingAssistant = requestHandlerBindingAssistant;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var showIssueQuery = extractQuery(request);
    if (!Method.GET.isSame(request.getMethod()) || !showIssueQuery.isValid()) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }
    telemetryService.showIssueRequestReceived();

    requestHandlerBindingAssistant.assistConnectionAndBindingIfNeededAsync(showIssueQuery.serverUrl, showIssueQuery.tokenName, showIssueQuery.tokenValue, showIssueQuery.projectKey,
      (connectionId, configScopeId) -> showIssueForScope(connectionId, configScopeId, showIssueQuery.issueKey, showIssueQuery.projectKey, showIssueQuery.branch,
        showIssueQuery.pullRequest));

    response.setCode(HttpStatus.SC_OK);
    response.setEntity(new StringEntity("OK"));
  }

  private void showIssueForScope(String connectionId, String configScopeId, String issueKey, String projectKey,
    String branch, @Nullable String pullRequest) {
    tryFetchIssue(connectionId, issueKey, projectKey, branch, pullRequest)
      .ifPresentOrElse(
        issueDetails -> client.showIssue(getShowIssueParams(issueDetails, connectionId, configScopeId, branch, pullRequest)),
        () -> client.showMessage(new ShowMessageParams(MessageType.ERROR, "Could not show the issue. See logs for more details")));
  }

  @VisibleForTesting
  ShowIssueParams getShowIssueParams(IssueApi.ServerIssueDetails issueDetails, String connectionId,
    String configScopeId, String branch, @Nullable String pullRequest) {
    var flowLocations = issueDetails.flowList.stream().map(flow -> {
      var locations = flow.getLocationsList().stream().map(location -> {
        var locationComponent = issueDetails.componentsList.stream().filter(component -> component.getKey().equals(location.getComponent())).findFirst();
        var filePath = locationComponent.map(Issues.Component::getPath).orElse("");
        var locationTextRange = location.getTextRange();
        var codeSnippet = tryFetchCodeSnippet(connectionId, locationComponent.map(Issues.Component::getKey).orElse(""), locationTextRange, branch, pullRequest);
        var locationTextRangeDto = new TextRangeDto(locationTextRange.getStartLine(), locationTextRange.getStartOffset(),
          locationTextRange.getEndLine(), locationTextRange.getEndOffset());
        return new LocationDto(locationTextRangeDto, location.getMsg(), filePath, codeSnippet.orElse(""));
      }).collect(Collectors.toList());
      return new FlowDto(locations);
    }).collect(Collectors.toList());

    var textRange = issueDetails.textRange;
    var textRangeDto = new TextRangeDto(textRange.getStartLine(), textRange.getStartOffset(), textRange.getEndLine(),
      textRange.getEndOffset());

    var isTaint = isIssueTaint(issueDetails.ruleKey);

    return new ShowIssueParams(configScopeId, new IssueDetailsDto(textRangeDto, issueDetails.ruleKey, issueDetails.key, issueDetails.path,
      branch, pullRequest, issueDetails.message, issueDetails.creationDate, issueDetails.codeSnippet, isTaint,
      flowLocations));
  }

  static boolean isIssueTaint(String ruleKey) {
    return RulesApi.TAINT_REPOS.stream().anyMatch(ruleKey::startsWith);
  }

  private Optional<IssueApi.ServerIssueDetails> tryFetchIssue(String connectionId, String issueKey, String projectKey, String branch, String pullRequest) {
    var serverApi = serverApiProvider.getServerApiOrThrow(connectionId);
    return serverApi.issue().fetchServerIssue(issueKey, projectKey, branch, pullRequest);
  }

  private Optional<String> tryFetchCodeSnippet(String connectionId, String fileKey, Common.TextRange textRange, String branch, String pullRequest) {
    var serverApi = serverApiProvider.getServerApi(connectionId);
    if (serverApi.isEmpty() || fileKey.isEmpty()) {
      // should not happen since we found the connection just before, improve the design ?
      return Optional.empty();
    }
    return serverApi.get().issue().getCodeSnippet(fileKey, textRange, branch, pullRequest);
  }

  @VisibleForTesting
  static ShowIssueQuery extractQuery(ClassicHttpRequest request) {
    var params = new HashMap<String, String>();
    try {
      new URIBuilder(request.getUri(), StandardCharsets.UTF_8)
        .getQueryParams()
        .forEach(p -> params.put(p.getName(), p.getValue()));
    } catch (URISyntaxException e) {
      // Ignored
    }
    return new ShowIssueQuery(params.get("server"), params.get("project"), params.get("issue"), params.get("branch"),
      params.get("pullRequest"), params.get("tokenName"), params.get("tokenValue"));
  }

  @VisibleForTesting
  public static class ShowIssueQuery {
    private final String serverUrl;
    private final String projectKey;
    private final String issueKey;
    private final String branch;
    @Nullable
    private final String pullRequest;
    @Nullable
    private final String tokenName;
    @Nullable
    private final String tokenValue;

    public ShowIssueQuery(String serverUrl, String projectKey, String issueKey, String branch,
      @Nullable String pullRequest, @Nullable String tokenName, @Nullable String tokenValue) {
      this.serverUrl = serverUrl;
      this.projectKey = projectKey;
      this.issueKey = issueKey;
      this.branch = branch;
      this.pullRequest = pullRequest;
      this.tokenName = tokenName;
      this.tokenValue = tokenValue;
    }

    public boolean isValid() {
      return isNotBlank(serverUrl) && isNotBlank(projectKey) && isNotBlank(issueKey) && isNotBlank(branch)
        && isPullRequestParamValid() && isTokenValid();
    }

    public boolean isPullRequestParamValid() {
      if (pullRequest != null) {
        return isNotEmpty(pullRequest);
      }
      return true;
    }

    /**
     * Either we get a token combination or we don't get a token combination: There is nothing in between
     */
    public boolean isTokenValid() {
      if (tokenName != null && tokenValue != null) {
        return isNotEmpty(tokenName) && isNotEmpty(tokenValue);
      }

      return tokenName == null && tokenValue == null;
    }

    public String getServerUrl() {
      return serverUrl;
    }

    public String getProjectKey() {
      return projectKey;
    }

    public String getIssueKey() {
      return issueKey;
    }

    public String getBranch() {
      return branch;
    }

    @Nullable
    public String getPullRequest() {
      return pullRequest;
    }

    @Nullable
    public String getTokenName() {
      return tokenName;
    }

    @Nullable
    public String getTokenValue() {
      return tokenValue;
    }
  }

}
