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
import jakarta.inject.Named;
import jakarta.inject.Singleton;
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
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Named
@Singleton
public class ShowHotspotRequestHandler implements HttpRequestHandler {
  private final SonarLintRpcClient client;
  private final ServerApiProvider serverApiProvider;
  private final TelemetryService telemetryService;
  private final RequestHandlerBindingAssistant requestHandlerBindingAssistant;
  private final PathTranslationService pathTranslationService;

  public ShowHotspotRequestHandler(SonarLintRpcClient client, ServerApiProvider serverApiProvider, TelemetryService telemetryService,
    RequestHandlerBindingAssistant requestHandlerBindingAssistant, PathTranslationService pathTranslationService) {
    this.client = client;
    this.serverApiProvider = serverApiProvider;
    this.telemetryService = telemetryService;
    this.requestHandlerBindingAssistant = requestHandlerBindingAssistant;
    this.pathTranslationService = pathTranslationService;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var showHotspotQuery = extractQuery(request);
    if (!Method.GET.isSame(request.getMethod()) || !showHotspotQuery.isValid()) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }
    telemetryService.showHotspotRequestReceived();

    requestHandlerBindingAssistant.assistConnectionAndBindingIfNeededAsync(showHotspotQuery.serverUrl, null, null, showHotspotQuery.projectKey,
      (connectionId, configScopeId, cancelMonitor) -> {
        if (configScopeId != null) {
          showHotspotForScope(connectionId, configScopeId, showHotspotQuery.hotspotKey, cancelMonitor);
        }
      });

    response.setCode(HttpStatus.SC_OK);
    response.setEntity(new StringEntity("OK"));
  }

  private void showHotspotForScope(String connectionId, String configurationScopeId, String hotspotKey, SonarLintCancelMonitor cancelMonitor) {
    var hotspotOpt = tryFetchHotspot(connectionId, hotspotKey, cancelMonitor);
    if (hotspotOpt.isPresent()) {
      pathTranslationService.getOrComputePathTranslation(configurationScopeId)
        .ifPresent(translation -> client.showHotspot(new ShowHotspotParams(configurationScopeId, adapt(hotspotKey, hotspotOpt.get(), translation))));
    } else {
      client.showMessage(new ShowMessageParams(MessageType.ERROR, "Could not show the hotspot. See logs for more details"));
    }
  }

  private Optional<ServerHotspotDetails> tryFetchHotspot(String connectionId, String hotspotKey, SonarLintCancelMonitor cancelMonitor) {
    var serverApi = serverApiProvider.getServerApi(connectionId);
    if (serverApi.isEmpty()) {
      // should not happen since we found the connection just before, improve the design ?
      return Optional.empty();
    }
    return serverApi.get().hotspot().fetch(hotspotKey, cancelMonitor);
  }

  private static HotspotDetailsDto adapt(String hotspotKey, ServerHotspotDetails hotspot, FilePathTranslation translation) {
    return new HotspotDetailsDto(
      hotspotKey,
      hotspot.message,
      translation.serverToIdePath(hotspot.filePath),
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
