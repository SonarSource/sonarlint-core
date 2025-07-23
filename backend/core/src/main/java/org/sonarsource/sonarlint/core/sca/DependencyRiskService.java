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
package org.sonarsource.sonarlint.core.sca;

import java.util.UUID;
import javax.annotation.CheckForNull;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.AffectedPackageDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.RecommendationDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.sca.GetIssueReleaseResponse;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

public class DependencyRiskService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final StorageService storageService;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final SonarLintRpcClient client;
  private final TelemetryService telemetryService;

  public DependencyRiskService(ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionRepository, StorageService storageService,
    SonarQubeClientManager sonarQubeClientManager, SonarProjectBranchTrackingService branchTrackingService, SonarLintRpcClient client, TelemetryService telemetryService) {
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
    this.storageService = storageService;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.branchTrackingService = branchTrackingService;
    this.client = client;
    this.telemetryService = telemetryService;
  }

  public void changeStatus(String configurationScopeId, UUID dependencyRiskKey, DependencyRiskTransition transition, @CheckForNull String comment,
    SonarLintCancelMonitor cancelMonitor) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var serverConnection = sonarQubeClientManager.getClientOrThrow(binding.connectionId());
    var projectServerIssueStore = storageService.binding(binding).findings();
    var branchName = branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId);

    if (branchName.isEmpty()) {
      throw new IllegalArgumentException("Could not determine matched branch for configuration scope " + configurationScopeId);
    }

    var dependencyRisks = projectServerIssueStore.loadDependencyRisks(branchName.get());
    var dependencyRiskOpt = dependencyRisks.stream().filter(risk -> risk.key().equals(dependencyRiskKey)).findFirst();

    if (dependencyRiskOpt.isEmpty()) {
      throw new DependencyRiskNotFoundException("Dependency Risk with key " + dependencyRiskKey + " was not found", dependencyRiskKey.toString());
    }

    var dependencyRisk = dependencyRiskOpt.get();

    if (!dependencyRisk.transitions().contains(adaptTransition(transition))) {
      throw new IllegalArgumentException("Transition " + transition + " is not allowed for this dependency risk");
    }

    if ((transition == DependencyRiskTransition.ACCEPT || transition == DependencyRiskTransition.SAFE || transition == DependencyRiskTransition.FIXED)
      && (comment == null || comment.isBlank())) {
      throw new IllegalArgumentException("Comment is required for ACCEPT, FIXED, and SAFE transitions");
    }

    LOG.info("Changing status for dependency risk {} to {} with comment: {}", dependencyRiskKey, transition, comment);

    serverConnection.withClientApi(serverApi -> serverApi.sca().changeStatus(dependencyRiskKey, transition.name(), comment, cancelMonitor));
  }

  public GetDependencyRiskDetailsResponse getDependencyRiskDetails(String configurationScopeId, UUID dependencyRiskKey, SonarLintCancelMonitor cancelMonitor) {
    var configScope = configurationRepository.getConfigurationScope(configurationScopeId);
    if (configScope == null) {
      var error = new ResponseError(SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_FOUND, "The provided configuration scope does not exist: " + configurationScopeId, configurationScopeId);
      throw new ResponseErrorException(error);
    }
    var effectiveBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (effectiveBinding.isEmpty()) {
      var error = new ResponseError(SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_BOUND,
        "The provided configuration scope is not bound to a SonarQube/SonarCloud project: " + configurationScopeId, configurationScopeId);
      throw new ResponseErrorException(error);
    }
    var apiClient = sonarQubeClientManager.getClientOrThrow(effectiveBinding.get().connectionId());
    var serverResponse = apiClient.withClientApiAndReturn(serverApi -> serverApi.sca().getIssueRelease(dependencyRiskKey, cancelMonitor));
    return convertToRpcResponse(serverResponse);
  }

  private static ServerDependencyRisk.Transition adaptTransition(DependencyRiskTransition transition) {
    return switch (transition) {
      case REOPEN -> ServerDependencyRisk.Transition.REOPEN;
      case CONFIRM -> ServerDependencyRisk.Transition.CONFIRM;
      case ACCEPT -> ServerDependencyRisk.Transition.ACCEPT;
      case SAFE -> ServerDependencyRisk.Transition.SAFE;
      case FIXED -> ServerDependencyRisk.Transition.FIXED;
    };
  }

  private static GetDependencyRiskDetailsResponse convertToRpcResponse(GetIssueReleaseResponse serverResponse) {
    var affectedPackages = serverResponse.vulnerability().affectedPackages().stream()
      .map(pkg -> {
        var recommendationDetails = pkg.recommendationDetails();
        RecommendationDetailsDto recommendationDetailsDto = null;
        if (recommendationDetails != null) {
          recommendationDetailsDto = RecommendationDetailsDto.builder()
            .impactScore(recommendationDetails.impactScore())
            .impactDescription(recommendationDetails.impactDescription())
            .realIssue(recommendationDetails.realIssue())
            .falsePositiveReason(recommendationDetails.falsePositiveReason())
            .includesDev(recommendationDetails.includesDev())
            .specificMethodsAffected(recommendationDetails.specificMethodsAffected())
            .specificMethodsDescription(recommendationDetails.specificMethodsDescription())
            .otherConditions(recommendationDetails.otherConditions())
            .otherConditionsDescription(recommendationDetails.otherConditionsDescription())
            .workaroundAvailable(recommendationDetails.workaroundAvailable())
            .workaroundDescription(recommendationDetails.workaroundDescription())
            .visibility(recommendationDetails.visibility()).build();
        }
        return new AffectedPackageDto(pkg.purl(), pkg.recommendation(), recommendationDetailsDto);
      })
      .toList();

    return new GetDependencyRiskDetailsResponse(serverResponse.key(), DependencyRiskDto.Severity.valueOf(serverResponse.severity().name()), serverResponse.release().packageName(),
      serverResponse.release().version(), DependencyRiskDto.Type.valueOf(serverResponse.type().name()), serverResponse.vulnerability().vulnerabilityId(),
      serverResponse.vulnerability().description(), affectedPackages);
  }

  public void openDependencyRiskInBrowser(String configurationScopeId, UUID dependencyKey) {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    var endpointParams = effectiveBinding.flatMap(binding -> connectionRepository.getEndpointParams(binding.connectionId()));
    if (effectiveBinding.isEmpty() || endpointParams.isEmpty()) {
      throw new IllegalArgumentException(String.format("Configuration scope '%s' is not bound properly, unable to open dependency risk", configurationScopeId));
    }
    var branchName = branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId);
    if (branchName.isEmpty()) {
      throw new IllegalArgumentException(String.format("Configuration scope %s has no matching branch, unable to open dependency risk", configurationScopeId));
    }

    var url = buildDependencyRiskBrowseUrl(effectiveBinding.get().sonarProjectKey(), branchName.get(), dependencyKey, endpointParams.get());

    client.openUrlInBrowser(new OpenUrlInBrowserParams(url));

    telemetryService.dependencyRiskInvestigatedRemotely();
  }

  static String buildDependencyRiskBrowseUrl(String projectKey, String branch, UUID dependencyKey, EndpointParams endpointParams) {
    var relativePath = new StringBuilder("/dependency-risks/")
      .append(UrlUtils.urlEncode(dependencyKey.toString()))
      .append("/what?id=")
      .append(UrlUtils.urlEncode(projectKey))
      .append("&branch=")
      .append(UrlUtils.urlEncode(branch))
      .toString();

    return ServerApiHelper.concat(endpointParams.getBaseUrl(), relativePath);
  }

  public static class DependencyRiskNotFoundException extends RuntimeException {
    private final String key;

    public DependencyRiskNotFoundException(String message, String key) {
      super(message);
      this.key = key;
    }

    public String getKey() {
      return key;
    }
  }
}
