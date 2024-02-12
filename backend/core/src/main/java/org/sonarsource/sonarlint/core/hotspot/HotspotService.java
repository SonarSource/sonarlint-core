/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.reporting.FindingReportingService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.tracking.TaintVulnerabilityTrackingService;
import org.springframework.context.event.EventListener;

@Named
@Singleton
public class HotspotService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String NO_BINDING_REASON = "The project is not bound, please bind it to SonarQube (Server, Cloud)";

  private static final String REVIEW_STATUS_UPDATE_PERMISSION_MISSING_REASON = "Changing a hotspot's status requires the 'Administer Security Hotspot' permission.";
  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;

  private final ConnectionManager connectionManager;
  private final TelemetryService telemetryService;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final FindingReportingService findingReportingService;
  private final StorageService storageService;

  public HotspotService(SonarLintRpcClient client, StorageService storageService, ConfigurationRepository configurationRepository,
    ConnectionConfigurationRepository connectionRepository, ConnectionManager connectionManager, TelemetryService telemetryService,
    SonarProjectBranchTrackingService branchTrackingService, FindingReportingService findingReportingService) {
    this.client = client;
    this.storageService = storageService;
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
    this.connectionManager = connectionManager;
    this.telemetryService = telemetryService;
    this.branchTrackingService = branchTrackingService;
    this.findingReportingService = findingReportingService;
  }

  public void openHotspotInBrowser(String configScopeId, String hotspotKey) {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
    var endpointParams = effectiveBinding.flatMap(binding -> connectionRepository.getEndpointParams(binding.getConnectionId()));
    if (effectiveBinding.isEmpty() || endpointParams.isEmpty()) {
      LOG.warn("Configuration scope {} is not bound properly, unable to open hotspot", configScopeId);
      return;
    }
    var branchName = branchTrackingService.awaitEffectiveSonarProjectBranch(configScopeId);
    if (branchName.isEmpty()) {
      LOG.warn("Configuration scope {} has no matching branch, unable to open hotspot", configScopeId);
      return;
    }

    var url = buildHotspotUrl(effectiveBinding.get().getSonarProjectKey(), branchName.get(), hotspotKey, endpointParams.get());

    client.openUrlInBrowser(new OpenUrlInBrowserParams(url));

    telemetryService.hotspotOpenedInBrowser();
  }

  public CheckLocalDetectionSupportedResponse checkLocalDetectionSupported(String configScopeId) {
    var configScope = configurationRepository.getConfigurationScope(configScopeId);
    if (configScope == null) {
      var error = new ResponseError(SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_FOUND, "The provided configuration scope does not exist: " + configScopeId, configScopeId);
      throw new ResponseErrorException(error);
    }
    var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
    if (effectiveBinding.isEmpty()) {
      return new CheckLocalDetectionSupportedResponse(false, NO_BINDING_REASON);
    }
    var connectionId = effectiveBinding.get().getConnectionId();
    if (connectionRepository.getConnectionById(connectionId) == null) {
      var error = new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND, "The provided configuration scope is bound to an unknown connection: " + connectionId,
        connectionId);
      throw new ResponseErrorException(error);
    }

    return new CheckLocalDetectionSupportedResponse(true, null);
  }

  public CheckStatusChangePermittedResponse checkStatusChangePermitted(String connectionId, String hotspotKey, SonarLintCancelMonitor cancelMonitor) {
    // fixme add getConnectionByIdOrThrow
    var connection = connectionRepository.getConnectionById(connectionId);
    var r = connectionManager.getConnectionOrThrow(connectionId)
      .withClientApiAndReturn(serverApi -> serverApi.hotspot().show(hotspotKey, cancelMonitor));
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
        .toList());
  }

  public void changeStatus(String configurationScopeId, String hotspotKey, HotspotReviewStatus newStatus, SonarLintCancelMonitor cancelMonitor) {
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (effectiveBindingOpt.isEmpty()) {
      LOG.debug("No binding for config scope {}", configurationScopeId);
      return;
    }
    connectionManager.withValidConnection(effectiveBindingOpt.get().getConnectionId(), serverApi -> {
      serverApi.hotspot().changeStatus(hotspotKey, newStatus, cancelMonitor);
      saveStatusInStorage(effectiveBindingOpt.get(), hotspotKey, newStatus);
      telemetryService.hotspotStatusChanged();
    });
  }

  private void saveStatusInStorage(Binding binding, String hotspotKey, HotspotReviewStatus newStatus) {
    storageService.binding(binding)
      .findings()
      .changeHotspotStatus(hotspotKey, newStatus);
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

  @EventListener
  public void onServerEventReceived(SonarServerEventReceivedEvent event) {
    var connectionId = event.getConnectionId();
    var serverEvent = event.getEvent();
    if (serverEvent instanceof SecurityHotspotChangedEvent hotspotChangedEvent) {
      updateStorage(connectionId, hotspotChangedEvent);
      republishPreviouslyRaisedHotspots(connectionId, hotspotChangedEvent);
    } else if (serverEvent instanceof SecurityHotspotClosedEvent hotspotClosedEvent) {
      updateStorage(connectionId, hotspotClosedEvent);
      republishPreviouslyRaisedHotspots(connectionId, hotspotClosedEvent);
    } else if (serverEvent instanceof SecurityHotspotRaisedEvent hotspotRaisedEvent) {
      // We could try to match with an existing hotspot. But we don't do it because we don't invest in hotspots right now.
      updateStorage(connectionId, hotspotRaisedEvent);
    }
  }

  private void updateStorage(String connectionId, SecurityHotspotRaisedEvent event) {
    var hotspot = new ServerHotspot(
      event.getHotspotKey(),
      event.getRuleKey(),
      event.getMainLocation().getMessage(),
      event.getMainLocation().getFilePath(),
      TaintVulnerabilityTrackingService.adapt(event.getMainLocation().getTextRange()),
      event.getCreationDate(),
      event.getStatus(),
      event.getVulnerabilityProbability(),
      null);
    var projectKey = event.getProjectKey();
    storageService.connection(connectionId).project(projectKey).findings().insert(event.getBranch(), hotspot);
  }

  private void updateStorage(String connectionId, SecurityHotspotClosedEvent event) {
    var projectKey = event.getProjectKey();
    storageService.connection(connectionId).project(projectKey).findings().deleteHotspot(event.getHotspotKey());
  }

  private void updateStorage(String connectionId, SecurityHotspotChangedEvent event) {
    var projectKey = event.getProjectKey();
    storageService.connection(connectionId).project(projectKey).findings().updateHotspot(event.getHotspotKey(), hotspot -> {
      var status = event.getStatus();
      if (status != null) {
        hotspot.setStatus(status);
      }
      var assignee = event.getAssignee();
      if (assignee != null) {
        hotspot.setAssignee(assignee);
      }
    });
  }

  private void republishPreviouslyRaisedHotspots(String connectionId, SecurityHotspotChangedEvent event) {
    var boundScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, event.getProjectKey());
    boundScopes.forEach(scope -> {
      var scopeId = scope.getConfigScopeId();
      findingReportingService.updateAndReportHotspots(scopeId,
        raisedHotspotDto -> changedHotspotUpdater(raisedHotspotDto, event));
    });
  }

  private static RaisedHotspotDto changedHotspotUpdater(RaisedHotspotDto raisedHotspotDto, SecurityHotspotChangedEvent event) {
    if (event.getHotspotKey().equals(raisedHotspotDto.getServerKey())) {
      return raisedHotspotDto.builder().withHotspotStatus(HotspotStatus.valueOf(event.getStatus().name())).buildHotspot();
    }
    return raisedHotspotDto;
  }

  private void republishPreviouslyRaisedHotspots(String connectionId, SecurityHotspotClosedEvent event) {
    var boundScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, event.getProjectKey());
    boundScopes.forEach(scope -> {
      var scopeId = scope.getConfigScopeId();
      findingReportingService.updateAndReportHotspots(scopeId,
        raisedHotspotDto -> closedHotspotUpdater(raisedHotspotDto, event));
    });
  }

  private static RaisedHotspotDto closedHotspotUpdater(RaisedHotspotDto raisedHotspotDto, SecurityHotspotClosedEvent event) {
    if (event.getHotspotKey().equals(raisedHotspotDto.getServerKey())) {
      return null;
    }
    return raisedHotspotDto;
  }
}
