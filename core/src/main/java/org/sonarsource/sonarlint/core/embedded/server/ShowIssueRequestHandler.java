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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarlint.core.BindingSuggestionProviderImpl;
import org.sonarsource.sonarlint.core.ConfigurationServiceImpl;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.MessageType;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.clientapi.common.FlowDto;
import org.sonarsource.sonarlint.core.clientapi.common.LocationDto;
import org.sonarsource.sonarlint.core.clientapi.common.TextRangeDto;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Named
@Singleton
public class ShowIssueRequestHandler extends ShowHotspotOrIssueRequestHandler implements HttpRequestHandler {

  private final SonarLintClient client;
  private final ConnectionConfigurationRepository repository;
  private final ConfigurationServiceImpl configurationService;
  private final ServerApiProvider serverApiProvider;
  private final TelemetryServiceImpl telemetryService;

  public ShowIssueRequestHandler(SonarLintClient client, ConnectionConfigurationRepository repository,
    ConfigurationServiceImpl configurationService, BindingSuggestionProviderImpl bindingSuggestionProvider,
    ServerApiProvider serverApiProvider, TelemetryServiceImpl telemetryService) {
    super(bindingSuggestionProvider, client);
    this.client = client;
    this.repository = repository;
    this.configurationService = configurationService;
    this.serverApiProvider = serverApiProvider;
    this.telemetryService = telemetryService;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var showIssueQuery = extractQuery(request);
    if (!showIssueQuery.isValid()) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    CompletableFuture.runAsync(() -> showIssue(showIssueQuery));

    response.setCode(HttpStatus.SC_OK);
    response.setEntity(new StringEntity("OK"));
  }

  @VisibleForTesting
  public void showIssue(ShowIssueQuery query) {
    telemetryService.showIssueRequestReceived();

    var connectionsMatchingOrigin = repository.findByUrl(query.serverUrl);
    if (connectionsMatchingOrigin.isEmpty()) {
      startFullBindingProcess();
      assistCreatingConnection(query.serverUrl)
        .thenCompose(response -> assistBinding(response.getNewConnectionId(), query.projectKey))
        .thenAccept(response -> showIssueForScope(response.getConnectionId(), response.getConfigurationScopeId(), query.issueKey))
        .whenComplete((v, e) -> endFullBindingProcess());
    } else {
      // we pick the first connection but this could lead to issues later if there were several matches (make the user select the right
      // one?)
      showIssueForConnection(connectionsMatchingOrigin.get(0).getConnectionId(), query.projectKey, query.issueKey);
    }
  }

  private void showIssueForConnection(String connectionId, String projectKey, String issueKey) {
    var scopes = configurationService.getConfigScopesWithBindingConfiguredTo(connectionId, projectKey);
    if (scopes.isEmpty()) {
      assistBinding(connectionId, projectKey)
        .thenAccept(newBinding -> showIssueForScope(connectionId, newBinding.getConfigurationScopeId(), issueKey));
    } else {
      // we pick the first bound scope but this could lead to issues later if there were several matches (make the user select the right one?)
      showIssueForScope(connectionId, scopes.get(0).getId(), issueKey);
    }
  }

  private void showIssueForScope(String connectionId, String configScopeId, String issueKey) {
    tryFetchIssue(connectionId, issueKey)
      .ifPresentOrElse(
        issueDetails -> client.showIssue(getShowIssueParams(issueDetails, connectionId, configScopeId)),
        () -> client.showMessage(new ShowMessageParams(MessageType.ERROR, "Could not show the issue. See logs for more details")));
  }

  @VisibleForTesting
  ShowIssueParams getShowIssueParams(IssueApi.ServerIssueDetails issueDetails, String connectionId, String configScopeId) {
    var flowLocations = issueDetails.flowList.stream().map(flow -> {
      var locations = flow.getLocationsList().stream().map(location -> {
        var locationComponent =
          issueDetails.componentsList.stream().filter(component -> component.getKey().equals(location.getComponent())).findFirst();
        var filePath = locationComponent.map(Issues.Component::getPath).orElse("");
        var locationTextRange = location.getTextRange();
        var codeSnippet = tryFetchCodeSnippet(connectionId, locationComponent.map(Issues.Component::getKey).orElse(""), locationTextRange);
        var locationTextRangeDto = new TextRangeDto(locationTextRange.getStartLine(), locationTextRange.getStartOffset(),
          locationTextRange.getEndLine(), locationTextRange.getEndOffset());
        return new LocationDto(locationTextRangeDto, location.getMsg(), filePath, codeSnippet.orElse(""));
      }).collect(Collectors.toList());
      return new FlowDto(locations);
    }).collect(Collectors.toList());

    var textRange = issueDetails.textRange;
    var textRangeDto = new TextRangeDto(textRange.getStartLine(), textRange.getStartOffset(), textRange.getEndLine(),
      textRange.getEndOffset());

    return new ShowIssueParams(textRangeDto, configScopeId, issueDetails.ruleKey, issueDetails.key, issueDetails.path, issueDetails.message,
      issueDetails.creationDate, issueDetails.codeSnippet, flowLocations);
  }

  private Optional<IssueApi.ServerIssueDetails> tryFetchIssue(String connectionId, String issueKey) {
    var serverApi = serverApiProvider.getServerApi(connectionId);
    if (serverApi.isEmpty()) {
      // should not happen since we found the connection just before, improve the design ?
      return Optional.empty();
    }
    return serverApi.get().issue().fetchServerIssue(issueKey);
  }

  private Optional<String> tryFetchCodeSnippet(String connectionId, String fileKey, Common.TextRange textRange) {
    var serverApi = serverApiProvider.getServerApi(connectionId);
    if (serverApi.isEmpty() || fileKey.isEmpty()) {
      // should not happen since we found the connection just before, improve the design ?
      return Optional.empty();
    }
    return serverApi.get().issue().getCodeSnippet(fileKey, textRange);
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
    return new ShowIssueQuery(params.get("server"), params.get("project"), params.get("issue"));
  }

  @VisibleForTesting
  public static class ShowIssueQuery {
    private final String serverUrl;
    private final String projectKey;
    private final String issueKey;

    public ShowIssueQuery(String serverUrl, String projectKey, String issueKey) {
      this.serverUrl = serverUrl;
      this.projectKey = projectKey;
      this.issueKey = issueKey;
    }

    public boolean isValid() {
      return isNotBlank(serverUrl) && isNotBlank(projectKey) && isNotBlank(issueKey);
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
  }
}
