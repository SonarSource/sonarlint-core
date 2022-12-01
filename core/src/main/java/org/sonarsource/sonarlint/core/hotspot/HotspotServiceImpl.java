/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.hotspot;

import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.clientapi.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;

public class HotspotServiceImpl implements HotspotService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintClient client;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;

  public HotspotServiceImpl(SonarLintClient client, ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionRepository) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
  }

  @Override
  public void openHotspotInBrowser(OpenHotspotInBrowserParams params) {
    var effectiveBinding = configurationRepository.getEffectiveBinding(params.getConfigScopeId());
    var endpointParams = effectiveBinding.flatMap(binding -> connectionRepository.getEndpointParams(binding.getConnectionId()));
    if (effectiveBinding.isEmpty() || endpointParams.isEmpty()) {
      LOG.warn("Configuration scope {} is not bound properly, unable to open hotspot", params.getConfigScopeId());
      return;
    }

    var url = buildHotspotUrl(effectiveBinding.get().getSonarProjectKey(), params.getBranch(), params.getHotspotKey(), endpointParams.get());

    client.openUrlInBrowser(new OpenUrlInBrowserParams(url));
  }

  static String buildHotspotUrl(String projectKey, String branch, String hotspotKey, EndpointParams endpointParams) {
    var relativePath = (endpointParams.isSonarCloud() ? "/project/security_hotspots?id=" : "/security_hotspots?id=")
      + UrlUtils.urlEncode(projectKey)
      + "&branch="
      + UrlUtils.urlEncode(branch)
      + "&hotspots="
      + UrlUtils.urlEncode(hotspotKey);

    return ServerApiHelper.concat(endpointParams.getBaseUrl(), relativePath);
  }
}
