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

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.BackendErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.utils.FutureUtils;

import static org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi.TRACKING_COMPATIBLE_MIN_SQ_VERSION;

@Named
@Singleton
public class HotspotService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String NO_BINDING_REASON = "The project is not bound, please bind it to SonarQube 9.7+ or SonarCloud";
  private static final String UNSUPPORTED_SONARQUBE_REASON = "Security Hotspots detection is disabled with this version of SonarQube, " +
    "please bind it to SonarQube 9.7+ or SonarCloud";

  private static final String REVIEW_STATUS_UPDATE_PERMISSION_MISSING_REASON = "Changing a hotspot's status requires the 'Administer Security Hotspot' permission.";
  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;

  private final ServerApiProvider serverApiProvider;
  private final TelemetryService telemetryService;
  private final StorageService storageService;

  public HotspotService(SonarLintRpcClient client, StorageService storageService, ConfigurationRepository configurationRepository,
    ConnectionConfigurationRepository connectionRepository, ServerApiProvider serverApiProvider, TelemetryService telemetryService) {
    this.client = client;
    this.storageService = storageService;
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
    this.serverApiProvider = serverApiProvider;
    this.telemetryService = telemetryService;
  }

  public void openHotspotInBrowser(String configScopeId, String branch, String hotspotKey) {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
    var endpointParams = effectiveBinding.flatMap(binding -> connectionRepository.getEndpointParams(binding.getConnectionId()));
    if (effectiveBinding.isEmpty() || endpointParams.isEmpty()) {
      LOG.warn("Configuration scope {} is not bound properly, unable to open hotspot", configScopeId);
      return;
    }

    var url = buildHotspotUrl(effectiveBinding.get().getSonarProjectKey(), branch, hotspotKey, endpointParams.get());

    client.openUrlInBrowser(new OpenUrlInBrowserParams(url));

    telemetryService.hotspotOpenedInBrowser();
  }

  public CheckLocalDetectionSupportedResponse checkLocalDetectionSupported(String configScopeId) {
    var configScope = configurationRepository.getConfigurationScope(configScopeId);
    if (configScope == null) {
      ResponseError error = new ResponseError(BackendErrorCode.CONFIG_SCOPE_NOT_FOUND, "The provided configuration scope does not exist: " + configScopeId, configScopeId);
      throw new ResponseErrorException(error);
    }
    var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
    if (effectiveBinding.isEmpty()) {
      return new CheckLocalDetectionSupportedResponse(false, NO_BINDING_REASON);
    }
    var connectionId = effectiveBinding.get().getConnectionId();
    var connection = connectionRepository.getConnectionById(connectionId);
    if (connection == null) {
      ResponseError error = new ResponseError(BackendErrorCode.CONNECTION_NOT_FOUND, "The provided configuration scope is bound to an unknown connection: " + connectionId,
        connectionId);
      throw new ResponseErrorException(error);
    }

    var supported = isLocalDetectionSupported(connection.getKind() == ConnectionKind.SONARCLOUD, effectiveBinding.get().getConnectionId());
    return new CheckLocalDetectionSupportedResponse(supported, supported ? null : UNSUPPORTED_SONARQUBE_REASON);
  }

  public CheckStatusChangePermittedResponse checkStatusChangePermitted(String connectionId, String hotspotKey, CancelChecker cancelChecker) {
    // fixme add getConnectionByIdOrThrow
    var connection = connectionRepository.getConnectionById(connectionId);
    var serverApi = serverApiProvider.getServerApiOrThrow(connectionId);
    var r = FutureUtils.waitForTaskWithResult(cancelChecker, serverApi.hotspot().show(hotspotKey), "check permission on hostpot", Duration.ofMinutes(1));
    var allowedStatuses = HotspotReviewStatus.allowedStatusesOn(connection.getKind());
    // canChangeStatus is false when the 'Administer Hotspots' permission is missing
    // normally the 'Browse' permission is also required, but we assume it's present as the client knows the hotspot key
    return toResponse(r.canChangeStatus, allowedStatuses);
  }

  private static CheckStatusChangePermittedResponse toResponse(boolean canChangeStatus, List<HotspotReviewStatus> coreStatuses) {
    return new CheckStatusChangePermittedResponse(canChangeStatus,
      canChangeStatus ? null : REVIEW_STATUS_UPDATE_PERMISSION_MISSING_REASON,
      coreStatuses.stream().map(s -> HotspotStatus.valueOf(s.name()))
        // respect ordering of the client-api enum for the UI
        .sorted()
        .collect(Collectors.toList()));
  }

  public void changeStatus(String configurationScopeId, String hotspotKey, HotspotReviewStatus newStatus, CancelChecker cancelChecker) {
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (effectiveBindingOpt.isEmpty()) {
      LOG.debug("No binding for config scope {}", configurationScopeId);
      return;
    }
    var connectionOpt = serverApiProvider.getServerApi(effectiveBindingOpt.get().getConnectionId());
    if (connectionOpt.isEmpty()) {
      LOG.debug("Connection {} is gone", effectiveBindingOpt.get().getConnectionId());
      return;
    }
    FutureUtils.waitForHttpRequest(cancelChecker, connectionOpt.get().hotspot().changeStatusAsync(hotspotKey, newStatus), "change hotspot status");
    saveStatusInStorage(effectiveBindingOpt.get(), hotspotKey, newStatus);
    telemetryService.hotspotStatusChanged();
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
