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
package org.sonarsource.sonarlint.core.container.connected.hotspot;

import java.io.InputStream;
import java.util.Optional;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.connected.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.http.ConnectedModeEndpoint;
import org.sonarsource.sonarlint.core.http.SonarLintHttpClient;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class SecurityHotspotsService {
  private static final Logger LOG = Loggers.get(SecurityHotspotsService.class);

  private static final String HOTSPOTS_API_URL = "/api/hotspots/show.protobuf";

  private final SonarLintWsClient client;

  public SecurityHotspotsService(ConnectedModeEndpoint endpoint, SonarLintHttpClient client) {
    this.client = new SonarLintWsClient(endpoint, client);
  }

  public Optional<RemoteHotspot> fetch(GetSecurityHotspotRequestParams params) {
    try (SonarLintHttpClient.Response wsResponse = client.get(getUrl(params.hotspotKey, params.projectKey)); InputStream is = wsResponse.bodyAsStream()) {
      Hotspots.ShowWsResponse response = Hotspots.ShowWsResponse.parseFrom(is);
      return Optional.of(adapt(response));
    } catch (Exception e) {
      LOG.error("Error while fetching security hotspot", e);
    }
    return Optional.empty();
  }

  private static RemoteHotspot adapt(Hotspots.ShowWsResponse hotspot) {
    return new RemoteHotspot(
      hotspot.getMessage(),
      hotspot.getComponent().getPath(),
      convertTextRange(hotspot.getTextRange()),
      hotspot.getAuthor(),
      RemoteHotspot.Status.valueOf(hotspot.getStatus()),
      hotspot.hasResolution() ? RemoteHotspot.Resolution.valueOf(hotspot.getResolution()) : null,
      adapt(hotspot.getRule()));
  }

  private static RemoteHotspot.Rule adapt(Hotspots.Rule rule) {
    return new RemoteHotspot.Rule(rule.getKey(), rule.getName(), rule.getSecurityCategory(), RemoteHotspot.Rule.Probability.valueOf(rule.getVulnerabilityProbability()),
      rule.getRiskDescription(), rule.getVulnerabilityDescription(), rule.getFixRecommendations());
  }

  private static String getUrl(String hotspotKey, String projectKey) {
    return HOTSPOTS_API_URL
      + "?projectKey=" + StringUtils.urlEncode(projectKey)
      + "&hotspot=" + StringUtils.urlEncode(hotspotKey);

  }

  private static TextRange convertTextRange(Common.TextRange textRange) {
    return new TextRange(textRange.getStartLine(), textRange.getStartOffset(), textRange.getEndLine(), textRange.getEndOffset());
  }
}
