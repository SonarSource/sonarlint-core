/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
import org.sonarsource.sonarlint.core.BindingSuggestionProviderImpl;
import org.sonarsource.sonarlint.core.ConfigurationServiceImpl;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Named
@Singleton
public class ShowHotspotRequestHandler extends ShowHotspotOrIssueRequestHandler implements HttpRequestHandler {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintClient client;
  private final ConnectionConfigurationRepository repository;
  private final ConfigurationServiceImpl configurationService;
  private final ServerApiProvider serverApiProvider;
  private final TelemetryServiceImpl telemetryService;

  public ShowHotspotRequestHandler(SonarLintClient client, ConnectionConfigurationRepository repository, ConfigurationServiceImpl configurationService,
    BindingSuggestionProviderImpl bindingSuggestionProvider, ServerApiProvider serverApiProvider, TelemetryServiceImpl telemetryService) {
    super(bindingSuggestionProvider, client);
    this.client = client;
    this.repository = repository;
    this.configurationService = configurationService;
    this.serverApiProvider = serverApiProvider;
    this.telemetryService = telemetryService;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var showHotspotQuery = extractQuery(request);
    if (!Method.GET.isSame(request.getMethod()) || !showHotspotQuery.isValid()) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    CompletableFuture.runAsync(() -> showHotspot(showHotspotQuery)).exceptionally(e -> {
      LOG.error("Unable to show hotspot", e);
      return null;
    });

    response.setCode(HttpStatus.SC_OK);
    response.setEntity(new StringEntity("OK"));
  }

  private void showHotspot(ShowHotspotQuery query) {
    telemetryService.showHotspotRequestReceived();

    var connectionsMatchingOrigin = repository.findByUrl(query.serverUrl);
    if (connectionsMatchingOrigin.isEmpty()) {
      startFullBindingProcess();
      try {
        var assistNewConnectionResult = assistCreatingConnection(query.serverUrl).get(10, TimeUnit.MINUTES);
        var assistNewBindingResult = assistBinding(assistNewConnectionResult.getNewConnectionId(), query.projectKey).get(10, TimeUnit.MINUTES);
        showHotspotForScope(assistNewConnectionResult.getNewConnectionId(), assistNewBindingResult.getConfigurationScopeId(), query.hotspotKey);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      } finally {
        endFullBindingProcess();
      }
    } else {
      // we pick the first connection but this could lead to issues later if there were several matches (make the user select the right one?)
      showHotspotForConnection(connectionsMatchingOrigin.get(0).getConnectionId(), query.projectKey, query.hotspotKey);
    }
  }

  private void showHotspotForConnection(String connectionId, String projectKey, String hotspotKey) {
    var scopes = configurationService.getConfigScopesWithBindingConfiguredTo(connectionId, projectKey);
    if (scopes.isEmpty()) {
      try {
        var assistNewBindingResult = assistBinding(connectionId, projectKey).get(10, TimeUnit.MINUTES);
        showHotspotForScope(connectionId, assistNewBindingResult.getConfigurationScopeId(), hotspotKey);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    } else {
      // we pick the first bound scope but this could lead to issues later if there were several matches (make the user select the right one?)
      var firstBoundScope = scopes.get(0);
      showHotspotForScope(connectionId, firstBoundScope.getId(), hotspotKey);
    }
  }

  private void showHotspotForScope(String connectionId, String configurationScopeId, String hotspotKey) {
    tryFetchHotspot(connectionId, hotspotKey)
      .ifPresentOrElse(
        hotspot -> client.showHotspot(new ShowHotspotParams(configurationScopeId, adapt(hotspotKey, hotspot))),
        () -> client.showMessage(new ShowMessageParams(MessageType.ERROR, "Could not show the hotspot. See logs for more details")));
  }

  private Optional<ServerHotspotDetails> tryFetchHotspot(String connectionId, String hotspotKey) {
    var serverApi = serverApiProvider.getServerApi(connectionId);
    if (serverApi.isEmpty()) {
      // should not happen since we found the connection just before, improve the design ?
      return Optional.empty();
    }
    return serverApi.get().hotspot().fetch(hotspotKey);
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

  private static TextRangeDto adapt(TextRange textRange) {
    return new TextRangeDto(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
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
