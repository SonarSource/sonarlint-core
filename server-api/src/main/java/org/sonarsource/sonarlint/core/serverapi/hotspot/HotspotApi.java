/*
 * SonarLint Server API
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
package org.sonarsource.sonarlint.core.serverapi.hotspot;

import java.io.InputStream;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;
import org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils;

public class HotspotApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String HOTSPOTS_API_URL = "/api/hotspots/show.protobuf";

  private final ServerApiHelper helper;

  public HotspotApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Optional<ServerHotspot> fetch(GetSecurityHotspotRequestParams params) {
    Hotspots.ShowWsResponse response;
    try (var wsResponse = helper.get(getUrl(params.hotspotKey, params.projectKey)); InputStream is = wsResponse.bodyAsStream()) {
      response = Hotspots.ShowWsResponse.parseFrom(is);
    } catch (Exception e) {
      LOG.error("Error while fetching security hotspot", e);
      return Optional.empty();
    }
    String fileKey = response.getComponent().getKey();
    Optional<String> source = new SourceApi(helper).getRawSourceCode(fileKey);
    String codeSnippet;
    if (source.isPresent()) {
      try {
        codeSnippet = ServerApiUtils.extractCodeSnippet(source.get(), response.getTextRange());
      } catch (Exception e) {
        LOG.debug("Unable to compute code snippet of '" + fileKey + "' for text range: " + response.getTextRange(), e);
        codeSnippet = null;
      }
    } else {
      codeSnippet = null;
    }
    return Optional.of(adapt(response, codeSnippet));
  }

  private static ServerHotspot adapt(Hotspots.ShowWsResponse hotspot, @Nullable String codeSnippet) {
    return new ServerHotspot(
      hotspot.getMessage(),
      hotspot.getComponent().getPath(),
      convertTextRange(hotspot.getTextRange()),
      hotspot.getAuthor(),
      ServerHotspot.Status.valueOf(hotspot.getStatus()),
      hotspot.hasResolution() ? ServerHotspot.Resolution.valueOf(hotspot.getResolution()) : null,
      adapt(hotspot.getRule()),
      codeSnippet);
  }

  private static ServerHotspot.Rule adapt(Hotspots.Rule rule) {
    return new ServerHotspot.Rule(rule.getKey(), rule.getName(), rule.getSecurityCategory(), ServerHotspot.Rule.Probability.valueOf(rule.getVulnerabilityProbability()),
      rule.getRiskDescription(), rule.getVulnerabilityDescription(), rule.getFixRecommendations());
  }

  private static String getUrl(String hotspotKey, String projectKey) {
    return HOTSPOTS_API_URL
      + "?projectKey=" + UrlUtils.urlEncode(projectKey)
      + "&hotspot=" + UrlUtils.urlEncode(hotspotKey);

  }

  private static ServerHotspot.TextRange convertTextRange(Common.TextRange textRange) {
    return new ServerHotspot.TextRange(textRange.getStartLine(), textRange.getStartOffset(), textRange.getEndLine(), textRange.getEndOffset());
  }
}
