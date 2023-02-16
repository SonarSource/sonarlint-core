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
package org.sonarsource.sonarlint.core.hotspot;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

import static org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi.TRACKING_COMPATIBLE_MIN_SQ_VERSION;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class HotspotServiceImpl implements HotspotService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String LOCAL_DETECTION_NOT_SUPPORTED_REASON = "The project is not bound to SonarQube 9.7+";

  private final SonarLintClient client;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;

  private final TelemetryServiceImpl telemetryService;
  private Path storageRoot;

  public HotspotServiceImpl(SonarLintClient client, ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionRepository,
    TelemetryServiceImpl telemetryService) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
    this.telemetryService = telemetryService;
  }

  public void initialize(Path storageRoot) {
    this.storageRoot = storageRoot;
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

    telemetryService.hotspotOpenedInBrowser();
  }

  @Override
  public CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(CheckLocalDetectionSupportedParams params) {
    var configScopeId = params.getConfigScopeId();
    boolean supported = false;
    if (configScopeId != null) {
      var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
      supported = effectiveBinding.flatMap(binding -> connectionRepository.getEndpointParams(binding.getConnectionId()))
        .map(ps -> isLocalDetectionSupported(ps.isSonarCloud(), effectiveBinding.get().getConnectionId()))
        .orElse(false);
    }
    return CompletableFuture.completedFuture(new CheckLocalDetectionSupportedResponse(supported, supported ? null : LOCAL_DETECTION_NOT_SUPPORTED_REASON));
  }

  private boolean isLocalDetectionSupported(boolean isSonarCloud, String connectionId) {
    return !isSonarCloud &&
      new ServerInfoStorage(storageRoot.resolve(encodeForFs(connectionId))).getServerInfo()
        .map(StoredServerInfo::getVersion)
        .map(version -> version.compareToIgnoreQualifier(TRACKING_COMPATIBLE_MIN_SQ_VERSION) >= 0)
        .orElse(false);
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
