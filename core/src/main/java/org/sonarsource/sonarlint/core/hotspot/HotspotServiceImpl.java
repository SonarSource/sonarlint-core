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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.ChangeHotspotStatusParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

import static org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi.TRACKING_COMPATIBLE_MIN_SQ_VERSION;

@Named
@Singleton
public class HotspotServiceImpl implements HotspotService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String NO_BINDING_REASON = "The project is not bound, please bind it to SonarQube 9.7+ or SonarCloud";
  private static final String UNSUPPORTED_SONARQUBE_REASON = "Security Hotspots detection is disabled with this version of SonarQube, " +
    "please bind it to SonarQube 9.7+ or SonarCloud";

  private static final String REVIEW_STATUS_UPDATE_PERMISSION_MISSING_REASON = "Changing a hotspot's status requires the 'Administer Security Hotspot' permission.";
  private final SonarLintClient client;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;

  private final ServerApiProvider serverApiProvider;
  private final TelemetryServiceImpl telemetryService;
  private final StorageService storageService;

  public HotspotServiceImpl(SonarLintClient client, StorageService storageService, ConfigurationRepository configurationRepository,
    ConnectionConfigurationRepository connectionRepository, ServerApiProvider serverApiProvider, TelemetryServiceImpl telemetryService) {
    this.client = client;
    this.storageService = storageService;
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
    this.serverApiProvider = serverApiProvider;
    this.telemetryService = telemetryService;
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
    var configScope = configurationRepository.getConfigurationScope(configScopeId);
    if (configScope == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("The provided configuration scope does not exist: " + configScopeId));
    }
    var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
    if (effectiveBinding.isEmpty()) {
      return CompletableFuture.completedFuture(new CheckLocalDetectionSupportedResponse(false, NO_BINDING_REASON));
    }
    var connectionId = effectiveBinding.get().getConnectionId();
    var connection = connectionRepository.getConnectionById(connectionId);
    if (connection == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("The provided configuration scope is bound to an unknown connection: " + connectionId));
    }

    var supported = isLocalDetectionSupported(connection.getKind() == ConnectionKind.SONARCLOUD, effectiveBinding.get().getConnectionId());
    return CompletableFuture.completedFuture(new CheckLocalDetectionSupportedResponse(supported, supported ? null : UNSUPPORTED_SONARQUBE_REASON));
  }

  @Override
  public CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params) {
    var connectionId = params.getConnectionId();
    var connection = connectionRepository.getConnectionById(connectionId);
    var serverApiOpt = serverApiProvider.getServerApi(connectionId);
    if (connection == null || serverApiOpt.isEmpty()) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("Connection with ID '" + connectionId + "' does not exist"));
    }
    return serverApiOpt.get().hotspot().show(params.getHotspotKey())
      .thenApply(hotspot -> {
        var allowedStatuses = HotspotReviewStatus.allowedStatusesOn(connection.getKind());
        // canChangeStatus is false when the 'Administer Hotspots' permission is missing
        // normally the 'Browse' permission is also required, but we assume it's present as the client knows the hotspot key
        return toResponse(hotspot.canChangeStatus, allowedStatuses);
      });
  }

  private static CheckStatusChangePermittedResponse toResponse(boolean canChangeStatus, List<HotspotReviewStatus> coreStatuses) {
    return new CheckStatusChangePermittedResponse(canChangeStatus,
      canChangeStatus ? null : REVIEW_STATUS_UPDATE_PERMISSION_MISSING_REASON,
      coreStatuses.stream().map(s -> HotspotStatus.valueOf(s.name()))
        // respect ordering of the client-api enum for the UI
        .sorted()
        .collect(Collectors.toList()));
  }

  @Override
  public CompletableFuture<Void> changeStatus(ChangeHotspotStatusParams params) {
    var configurationScopeId = params.getConfigurationScopeId();
    var optionalBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    return optionalBinding
      .flatMap(effectiveBinding -> serverApiProvider.getServerApi(effectiveBinding.getConnectionId()))
      .map(connection -> {
        var reviewStatus = toCore(params.getNewStatus());
        return connection.hotspot().changeStatusAsync(params.getHotspotKey(), reviewStatus)
          .thenAccept(nothing -> {
            saveStatusInStorage(optionalBinding.get(), params.getHotspotKey(), reviewStatus);
            telemetryService.hotspotStatusChanged();
          })
          .exceptionally(throwable -> {
            throw new HotspotStatusChangeException(throwable);
          });
      })
      .orElseGet(() -> CompletableFuture.completedFuture(null));
  }

  private static HotspotReviewStatus toCore(HotspotStatus newStatus) {
    return HotspotReviewStatus.valueOf(newStatus.name());
  }

  private void saveStatusInStorage(Binding binding, String hotspotKey, HotspotReviewStatus newStatus) {
    storageService.binding(binding)
      .findings()
      .changeHotspotStatus(hotspotKey, newStatus);
  }

  private boolean isLocalDetectionSupported(boolean isSonarCloud, String connectionId) {
    return isSonarCloud ||
      storageService.connection(connectionId).serverInfo().read()
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
