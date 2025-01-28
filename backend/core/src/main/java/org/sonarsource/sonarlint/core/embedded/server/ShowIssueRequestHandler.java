/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarQubeConnectionParams;
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
import org.sonarsource.sonarlint.core.sync.SonarProjectBranchesSynchronizationService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class ShowIssueRequestHandler implements HttpRequestHandler {

  private final SonarLintRpcClient client;
  private final ConnectionManager connectionManager;
  private final TelemetryService telemetryService;
  private final RequestHandlerBindingAssistant requestHandlerBindingAssistant;
  private final PathTranslationService pathTranslationService;
  private final SonarCloudActiveEnvironment sonarCloudActiveEnvironment;
  private final SonarProjectBranchesSynchronizationService sonarProjectBranchesSynchronizationService;

  public ShowIssueRequestHandler(SonarLintRpcClient client, ConnectionManager connectionManager, TelemetryService telemetryService,
    RequestHandlerBindingAssistant requestHandlerBindingAssistant, PathTranslationService pathTranslationService, SonarCloudActiveEnvironment sonarCloudActiveEnvironment,
    SonarProjectBranchesSynchronizationService sonarProjectBranchesSynchronizationService) {
    this.client = client;
    this.connectionManager = connectionManager;
    this.telemetryService = telemetryService;
    this.requestHandlerBindingAssistant = requestHandlerBindingAssistant;
    this.pathTranslationService = pathTranslationService;
    this.sonarCloudActiveEnvironment = sonarCloudActiveEnvironment;
    this.sonarProjectBranchesSynchronizationService = sonarProjectBranchesSynchronizationService;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var originHeader = request.getHeader("Origin");
    var origin = originHeader != null ? originHeader.getValue() : null;
    var showIssueQuery = extractQuery(request);
    if (origin == null || !Method.GET.isSame(request.getMethod()) || !showIssueQuery.isValid()) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }
    telemetryService.showIssueRequestReceived();

    AssistCreatingConnectionParams serverConnectionParams = createAssistServerConnectionParams(showIssueQuery);

    requestHandlerBindingAssistant.assistConnectionAndBindingIfNeededAsync(
      serverConnectionParams,
      showIssueQuery.projectKey,
      origin,
      (connectionId, boundScopes, configScopeId, cancelMonitor) -> {
        if (configScopeId != null) {
          var branchToMatch = showIssueQuery.branch;
          if (branchToMatch == null) {
            branchToMatch = sonarProjectBranchesSynchronizationService.findMainBranch(connectionId, showIssueQuery.projectKey, cancelMonitor);
          }
          var localBranchMatchesRequesting = client.matchProjectBranch(new MatchProjectBranchParams(configScopeId, branchToMatch)).join().isBranchMatched();
          if (!localBranchMatchesRequesting) {
            client.showMessage(new ShowMessageParams(MessageType.ERROR, "Attempted to show an issue from branch '" +
              StringEscapeUtils.escapeHtml(branchToMatch) + "', which is different from the currently checked-out branch." +
              "\nPlease switch to the correct branch and try again."));
            return;
          }
          showIssueForScope(connectionId, configScopeId, showIssueQuery.issueKey, showIssueQuery.projectKey, branchToMatch,
            showIssueQuery.pullRequest, cancelMonitor);
        }
      });

    response.setCode(HttpStatus.SC_OK);
    response.setEntity(new StringEntity("OK"));
  }

  private static AssistCreatingConnectionParams createAssistServerConnectionParams(ShowIssueQuery query) {
    var tokenName = query.getTokenName();
    var tokenValue = query.getTokenValue();
    return query.isSonarCloud ?
      new AssistCreatingConnectionParams(new SonarCloudConnectionParams(query.getOrganizationKey(), tokenName, tokenValue))
      : new AssistCreatingConnectionParams(new SonarQubeConnectionParams(query.getServerUrl(), tokenName, tokenValue));
  }

  private boolean isSonarCloud(ClassicHttpRequest request) throws ProtocolException {
    return Optional.ofNullable(request.getHeader("Origin"))
      .map(NameValuePair::getValue)
      .map(sonarCloudActiveEnvironment::isSonarQubeCloud)
      .orElse(false);
  }

  private void showIssueForScope(String connectionId, String configScopeId, String issueKey, String projectKey,
    String branch, @Nullable String pullRequest, SonarLintCancelMonitor cancelMonitor) {
    var issueDetailsOpt = tryFetchIssue(connectionId, issueKey, projectKey, branch, pullRequest, cancelMonitor);
    if (issueDetailsOpt.isPresent()) {
      pathTranslationService.getOrComputePathTranslation(configScopeId)
        .ifPresent(translation -> client.showIssue(getShowIssueParams(issueDetailsOpt.get(), connectionId, configScopeId, branch, pullRequest, translation, cancelMonitor)));
    } else {
      client.showMessage(new ShowMessageParams(MessageType.ERROR, "Could not show the issue. See logs for more details"));
    }
  }

  @VisibleForTesting
  ShowIssueParams getShowIssueParams(IssueApi.ServerIssueDetails issueDetails, String connectionId,
    String configScopeId, String branch, @Nullable String pullRequest, FilePathTranslation translation, SonarLintCancelMonitor cancelMonitor) {
    var flowLocations = issueDetails.flowList.stream().map(flow -> {
      var locations = flow.getLocationsList().stream().map(location -> {
        var locationComponent = issueDetails.componentsList.stream().filter(component -> component.getKey().equals(location.getComponent())).findFirst();
        var filePath = locationComponent.map(Issues.Component::getPath).orElse("");
        var locationTextRange = location.getTextRange();
        var codeSnippet = tryFetchCodeSnippet(connectionId, locationComponent.map(Issues.Component::getKey).orElse(""), locationTextRange, branch, pullRequest, cancelMonitor);
        var locationTextRangeDto = new TextRangeDto(locationTextRange.getStartLine(), locationTextRange.getStartOffset(),
          locationTextRange.getEndLine(), locationTextRange.getEndOffset());
        return new LocationDto(locationTextRangeDto, location.getMsg(), translation.serverToIdePath(Paths.get(filePath)), codeSnippet.orElse(""));
      }).toList();
      return new FlowDto(locations);
    }).toList();

    var textRange = issueDetails.textRange;
    var textRangeDto = new TextRangeDto(textRange.getStartLine(), textRange.getStartOffset(), textRange.getEndLine(),
      textRange.getEndOffset());

    var isTaint = isIssueTaint(issueDetails.ruleKey);

    return new ShowIssueParams(configScopeId, new IssueDetailsDto(textRangeDto, issueDetails.ruleKey, issueDetails.key, translation.serverToIdePath(issueDetails.path),
      issueDetails.message, issueDetails.creationDate, issueDetails.codeSnippet, isTaint, flowLocations));
  }

  static boolean isIssueTaint(String ruleKey) {
    return RulesApi.TAINT_REPOS.stream().anyMatch(ruleKey::startsWith);
  }

  private Optional<IssueApi.ServerIssueDetails> tryFetchIssue(String connectionId, String issueKey, String projectKey, String branch, @Nullable String pullRequest,
    SonarLintCancelMonitor cancelMonitor) {
    return connectionManager.withValidConnectionFlatMapOptionalAndReturn(connectionId,
      serverApi -> serverApi.issue().fetchServerIssue(issueKey, projectKey, branch, pullRequest, cancelMonitor));
  }

  private Optional<String> tryFetchCodeSnippet(String connectionId, String fileKey, Common.TextRange textRange, String branch, @Nullable String pullRequest,
    SonarLintCancelMonitor cancelMonitor) {
    return connectionManager.withValidConnectionFlatMapOptionalAndReturn(connectionId,
      api -> api.issue().getCodeSnippet(fileKey, textRange, branch, pullRequest, cancelMonitor));
  }

  @VisibleForTesting
  ShowIssueQuery extractQuery(ClassicHttpRequest request) throws ProtocolException {
    var params = new HashMap<String, String>();
    try {
      new URIBuilder(request.getUri(), StandardCharsets.UTF_8)
        .getQueryParams()
        .forEach(p -> params.put(p.getName(), p.getValue()));
    } catch (URISyntaxException e) {
      // Ignored
    }
    boolean isSonarCloud = isSonarCloud(request);
    String serverUrl;

    if (isSonarCloud) {
      var originUrl = request.getHeader("Origin").getValue();
      var region = sonarCloudActiveEnvironment.getRegion(originUrl);
      // Since the 'isSonarCloud' check passed, we are sure that the region will be there
      serverUrl = sonarCloudActiveEnvironment.getUri(region.get()).toString();
    } else {
      serverUrl = params.get("server");
    }
    return new ShowIssueQuery(serverUrl, params.get("project"), params.get("issue"), params.get("branch"),
      params.get("pullRequest"), params.get("tokenName"), params.get("tokenValue"), params.get("organizationKey"), isSonarCloud);
  }

  @VisibleForTesting
  public static class ShowIssueQuery {
    private final String serverUrl;
    private final String projectKey;
    private final String issueKey;
    @Nullable
    private final String branch;
    @Nullable
    private final String pullRequest;
    @Nullable
    private final String tokenName;
    @Nullable
    private final String tokenValue;
    @Nullable
    private final String organizationKey;
    private final boolean isSonarCloud;

    public ShowIssueQuery(@Nullable String serverUrl, String projectKey, String issueKey, @Nullable String branch, @Nullable String pullRequest,
      @Nullable String tokenName, @Nullable String tokenValue, @Nullable String organizationKey, boolean isSonarCloud) {
      this.serverUrl = serverUrl;
      this.projectKey = projectKey;
      this.issueKey = issueKey;
      this.branch = branch;
      this.pullRequest = pullRequest;
      this.tokenName = tokenName;
      this.tokenValue = tokenValue;
      this.organizationKey = organizationKey;
      this.isSonarCloud = isSonarCloud;
    }

    public boolean isValid() {
      return isNotBlank(projectKey) && isNotBlank(issueKey)
        && (isSonarCloud || isNotBlank(serverUrl))
        && (!isSonarCloud || isNotBlank(organizationKey))
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

    @Nullable
    public String getOrganizationKey() {
      return organizationKey;
    }

    public String getIssueKey() {
      return issueKey;
    }

    @Nullable
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
