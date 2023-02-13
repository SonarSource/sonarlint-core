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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.ConfigurationServiceImpl;
import org.sonarsource.sonarlint.core.ConnectionServiceImpl;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.MessageType;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.serverapi.hotspot.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

class ShowHotspotRequestHandler implements HttpRequestHandler {

  private final SonarLintClient client;
  private final ConnectionServiceImpl connectionService;
  private final ConfigurationServiceImpl configurationService;
  private final BindingSuggestionProvider bindingSuggestionProvider;
  private final ServerApiProvider serverApiProvider;
  private final TelemetryServiceImpl telemetryService;

  public ShowHotspotRequestHandler(SonarLintClient client, ConnectionServiceImpl connectionService, ConfigurationServiceImpl configurationService,
    BindingSuggestionProvider bindingSuggestionProvider, ServerApiProvider serverApiProvider, TelemetryServiceImpl telemetryService) {
    this.client = client;
    this.connectionService = connectionService;
    this.configurationService = configurationService;
    this.bindingSuggestionProvider = bindingSuggestionProvider;
    this.serverApiProvider = serverApiProvider;
    this.telemetryService = telemetryService;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var showHotspotQuery = extractQuery(request);
    if (!showHotspotQuery.isValid()) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    CompletableFuture.runAsync(() -> showHotspot(showHotspotQuery));

    response.setCode(HttpStatus.SC_OK);
    response.setEntity(new StringEntity("OK"));
  }

  private void showHotspot(ShowHotspotQuery query) {
    telemetryService.showHotspotRequestReceived();

    var connectionsMatchingOrigin = connectionService.findByUrl(query.serverUrl);
    if (connectionsMatchingOrigin.isEmpty()) {
      startFullBindingProcess();
      assistCreatingConnection(query.serverUrl)
        .thenCompose(response -> assistBinding(response.getNewConnectionId(), query.projectKey))
        .thenAccept(response -> showHotspotForScope(response.connectionId, response.configurationScopeId, query.projectKey, query.hotspotKey))
        .whenComplete((v, e) -> endFullBindingProcess());
    } else {
      // we pick the first connection but this could lead to issues later if there were several matches (make the user select the right one?)
      showHotspotForConnection(connectionsMatchingOrigin.get(0).getConnectionId(), query.projectKey, query.hotspotKey);
    }
  }

  private void startFullBindingProcess() {
    // we don't want binding suggestions to appear in the middle of a full binding creation process (connection + binding)
    // the other possibility would be to still notify the client anyway and let it handle UI interactions one at a time (assists, messages, suggestions, ...)
    bindingSuggestionProvider.disable();
  }

  private void endFullBindingProcess() {
    bindingSuggestionProvider.enable();
  }

  private void showHotspotForConnection(String connectionId, String projectKey, String hotspotKey) {
    var scopes = configurationService.getConfigScopesWithBindingConfiguredTo(connectionId, projectKey);
    if (scopes.isEmpty()) {
      assistBinding(connectionId, projectKey)
        .thenAccept(newBinding -> showHotspotForScope(connectionId, newBinding.configurationScopeId, projectKey, hotspotKey));
    } else {
      // we pick the first bound scope but this could lead to issues later if there were several matches (make the user select the right one?)
      var firstBoundScope = scopes.get(0);
      showHotspotForScope(connectionId, firstBoundScope.getId(), projectKey, hotspotKey);
    }
  }

  private void showHotspotForScope(String connectionId, String configurationScopeId, String projectKey, String hotspotKey) {
    tryFetchHotspot(connectionId, projectKey, hotspotKey)
      .ifPresentOrElse(
        hotspot -> client.showHotspot(new ShowHotspotParams(configurationScopeId, adapt(hotspotKey, hotspot))),
        () -> client.showMessage(new ShowMessageParams(MessageType.ERROR, "Could not show the hotspot. See logs for more details")));
  }

  private Optional<ServerHotspotDetails> tryFetchHotspot(String connectionId, String projectKey, String hotspotKey) {
    var serverApi = serverApiProvider.getServerApi(connectionId);
    if (serverApi.isEmpty()) {
      // should not happen since we found the connection just before, improve the design ?
      return Optional.empty();
    }
    return serverApi.get().hotspot().fetch(new GetSecurityHotspotRequestParams(hotspotKey, projectKey));
  }

  private CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(String serverUrl) {
    return client.assistCreatingConnection(new AssistCreatingConnectionParams(serverUrl));
  }

  private CompletableFuture<NewBinding> assistBinding(String connectionId, String projectKey) {
    return client.assistBinding(new AssistBindingParams(connectionId, projectKey))
      .thenApply(response -> new NewBinding(connectionId, response.getConfigurationScopeId()));
  }

  private static class NewBinding {
    private final String connectionId;
    private final String configurationScopeId;

    private NewBinding(String connectionId, String configurationScopeId) {
      this.connectionId = connectionId;
      this.configurationScopeId = configurationScopeId;
    }
  }

  private static HotspotDetailsDto adapt(String hotspotKey, ServerHotspotDetails hotspot) {
    return new HotspotDetailsDto(
      hotspotKey,
      hotspot.message,
      hotspot.filePath,
      adapt(hotspot.textRange),
      hotspot.author,
      hotspot.status.toString(),
      hotspot.resolution != null ? hotspot.resolution.toString() : null,
      adapt(hotspot.rule),
      hotspot.codeSnippet);
  }

  private static HotspotDetailsDto.HotspotRule adapt(ServerHotspotDetails.Rule rule) {
    return new HotspotDetailsDto.HotspotRule(
      rule.key,
      rule.name,
      rule.securityCategory,
      rule.vulnerabilityProbability.toString(),
      rule.riskDescription,
      rule.vulnerabilityDescription,
      rule.fixRecommendations);
  }

  private static HotspotDetailsDto.TextRangeDto adapt(TextRange textRange) {
    return new HotspotDetailsDto.TextRangeDto(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }

  private static ShowHotspotQuery extractQuery(ClassicHttpRequest request) {
    var params = new HashMap<String, String>();
    try {
      new URIBuilder(request.getUri(), StandardCharsets.UTF_8)
        .getQueryParams()
        .forEach(p -> params.put(p.getName(), p.getValue()));
    } catch (URISyntaxException e) {
      // Ignored
    }
    return new ShowHotspotQuery(params.get("server"), params.get("project"), params.get("hotspot"));
  }

  private static class ShowHotspotQuery {
    private final String serverUrl;
    private final String projectKey;
    private final String hotspotKey;

    private ShowHotspotQuery(String serverUrl, String projectKey, String hotspotKey) {
      this.serverUrl = serverUrl;
      this.projectKey = projectKey;
      this.hotspotKey = hotspotKey;
    }

    public boolean isValid() {
      return isNotBlank(serverUrl) && isNotBlank(projectKey) && isNotBlank(hotspotKey);
    }
  }
}
