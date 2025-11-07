/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.remediation.aicodefix;

import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.monitoring.DogfoodEnvironmentDetectionService;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.storage.model.AiCodeFix;
import org.sonarsource.sonarlint.core.event.FixSuggestionReceivedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.reporting.PreviouslyRaisedFindingsRepository;
import org.sonarsource.sonarlint.core.repository.reporting.RaisedIssue;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixChangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.exception.TooManyRequestsException;
import org.sonarsource.sonarlint.core.serverapi.fixsuggestions.AiSuggestionRequestBodyDto;
import org.sonarsource.sonarlint.core.serverapi.fixsuggestions.AiSuggestionResponseBodyDto;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixFeatureEnablement;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixSettings;
import org.sonarsource.sonarlint.core.commons.storage.repository.AiCodeFixRepository;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.tracking.TaintVulnerabilityTrackingService;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Objects.requireNonNull;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_BOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONNECTION_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.FILE_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.ISSUE_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.TOO_MANY_REQUESTS;

public class AiCodeFixService {
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConfigurationRepository configurationRepository;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final PreviouslyRaisedFindingsRepository previouslyRaisedFindingsRepository;
  private final ClientFileSystemService clientFileSystemService;
  private final StorageService storageService;
  private final ApplicationEventPublisher eventPublisher;
  private final TaintVulnerabilityTrackingService taintVulnerabilityTrackingService;
  private final AiCodeFixRepository aiCodeFixRepository;
  private final DogfoodEnvironmentDetectionService dogfoodEnvDetectionService;

  public AiCodeFixService(ConnectionConfigurationRepository connectionRepository, ConfigurationRepository configurationRepository, SonarQubeClientManager sonarQubeClientManager,
    PreviouslyRaisedFindingsRepository previouslyRaisedFindingsRepository, ClientFileSystemService clientFileSystemService,
    ApplicationEventPublisher eventPublisher, TaintVulnerabilityTrackingService taintVulnerabilityTrackingService, AiCodeFixRepository aiCodeFixRepository,
    StorageService storageService, DogfoodEnvironmentDetectionService dogfoodEnvDetectionService) {
    this.connectionRepository = connectionRepository;
    this.configurationRepository = configurationRepository;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.previouslyRaisedFindingsRepository = previouslyRaisedFindingsRepository;
    this.clientFileSystemService = clientFileSystemService;
    this.storageService = storageService;
    this.eventPublisher = eventPublisher;
    this.taintVulnerabilityTrackingService = taintVulnerabilityTrackingService;
    this.aiCodeFixRepository = aiCodeFixRepository;
    this.dogfoodEnvDetectionService = dogfoodEnvDetectionService;
  }

  public static AiCodeFixSettings aiCodeFixMapping(AiCodeFix entity) {
    return new AiCodeFixSettings(
      Sets.newHashSet(entity.supportedRules()),
      entity.organizationEligible(),
      AiCodeFixFeatureEnablement.valueOf(entity.enablement().name()),
      Sets.newHashSet(entity.enabledProjectKeys()));
  }

  public Optional<AiCodeFixFeature> getFeature(Binding binding) {
    if (dogfoodEnvDetectionService.isDogfoodEnvironment()) {
      return aiCodeFixRepository.get(binding.connectionId())
        .map(AiCodeFixService::aiCodeFixMapping)
        .filter(settings -> settings.isFeatureEnabled(binding.sonarProjectKey()))
        .map(AiCodeFixFeature::new);
    } else {
      return getFeature(storageService, binding);
    }
  }

  public static Optional<AiCodeFixFeature> getFeature(StorageService storageService, Binding binding) {
    return storageService.connection(binding.connectionId()).aiCodeFix().read()
      .map(AiCodeFixFeature::new);
  }

  public SuggestFixResponse suggestFix(String configurationScopeId, UUID issueId, SonarLintCancelMonitor cancelMonitor) {
    var bindingWithOrg = ensureBound(configurationScopeId);
    var connection = sonarQubeClientManager.getClientOrThrow(bindingWithOrg.binding().connectionId());
    var responseBodyDto = connection.withClientApiAndReturn(serverApi -> {
      var issueOptional = previouslyRaisedFindingsRepository.findRaisedIssueById(issueId);
      if (issueOptional.isPresent()) {
        return generateResponseBodyForIssue(serverApi, issueOptional.get(), issueId, bindingWithOrg, cancelMonitor);
      } else {
        var taintOptional = taintVulnerabilityTrackingService.getTaintVulnerability(configurationScopeId, issueId, cancelMonitor);
        if (taintOptional.isPresent()) {
          return generateResponseBodyForTaint(serverApi, taintOptional.get(), configurationScopeId, bindingWithOrg, cancelMonitor);
        } else {
          throw new ResponseErrorException(new ResponseError(ISSUE_NOT_FOUND, "The provided issue or taint does not exist", issueId));
        }
      }
    });
    return adapt(responseBodyDto);
  }

  private AiSuggestionResponseBodyDto generateResponseBodyForIssue(ServerApi serverApi, RaisedIssue raisedIssue, UUID issueId,
    BindingWithOrg bindingWithOrg, SonarLintCancelMonitor cancelMonitor) {
    var aiCodeFixFeature = getFeature(bindingWithOrg.binding());
    if (!aiCodeFixFeature.map(feature -> feature.isFixable(raisedIssue)).orElse(false)) {
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "The provided issue cannot be fixed", issueId));
    }

    AiSuggestionResponseBodyDto fixResponseDto;

    try {
      var requestBody = toDto(bindingWithOrg.organizationKey, bindingWithOrg.binding().sonarProjectKey(), raisedIssue);
      fixResponseDto = serverApi.fixSuggestions().getAiSuggestion(requestBody, cancelMonitor);
    } catch (TooManyRequestsException e) {
      throw new ResponseErrorException(new ResponseError(TOO_MANY_REQUESTS, "AI CodeFix usage has been capped. Too many requests have been made.", issueId));
    }

    eventPublisher.publishEvent(new FixSuggestionReceivedEvent(
      fixResponseDto.id().toString(),
      serverApi.isSonarCloud() ? AiSuggestionSource.SONARCLOUD : AiSuggestionSource.SONARQUBE,
      fixResponseDto.changes().size(),
      // As of today, this is always true since suggestFix is only called by the clients
      true));

    return fixResponseDto;
  }

  private AiSuggestionResponseBodyDto generateResponseBodyForTaint(ServerApi serverApi, TaintVulnerabilityDto taint,
    String configScopeId, BindingWithOrg bindingWithOrg, SonarLintCancelMonitor cancelMonitor) {
    var aiCodeFixFeature = getFeature(bindingWithOrg.binding());
    if (!aiCodeFixFeature.map(feature -> feature.isFixable(taint)).orElse(false)) {
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "The provided taint cannot be fixed", taint.getId()));
    }

    AiSuggestionResponseBodyDto fixResponseDto;

    try {
      var requestBody = toDto(bindingWithOrg.organizationKey, bindingWithOrg.binding().sonarProjectKey(), taint, configScopeId);
      fixResponseDto = serverApi.fixSuggestions().getAiSuggestion(requestBody, cancelMonitor);
    } catch (TooManyRequestsException e) {
      throw new ResponseErrorException(new ResponseError(TOO_MANY_REQUESTS, "AI CodeFix usage has been capped. Too many requests have been made.", taint.getId()));
    }

    eventPublisher.publishEvent(new FixSuggestionReceivedEvent(
      fixResponseDto.id().toString(),
      serverApi.isSonarCloud() ? AiSuggestionSource.SONARCLOUD : AiSuggestionSource.SONARQUBE,
      fixResponseDto.changes().size(),
      // As of today, this is always true since suggestFix is only called by the clients
      true));

    return fixResponseDto;
  }

  private static SuggestFixResponse adapt(AiSuggestionResponseBodyDto responseBodyDto) {
    return new SuggestFixResponse(responseBodyDto.id(), responseBodyDto.explanation(),
      responseBodyDto.changes().stream().map(change -> new SuggestFixChangeDto(change.startLine(), change.endLine(), change.newCode())).toList());
  }

  private BindingWithOrg ensureBound(String configurationScopeId) {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (effectiveBinding.isEmpty()) {
      throw new ResponseErrorException(new ResponseError(CONFIG_SCOPE_NOT_BOUND, "The provided configuration scope is not bound", configurationScopeId));
    }
    var binding = effectiveBinding.get();
    var connection = connectionRepository.getConnectionById(binding.connectionId());
    if (connection == null) {
      throw new ResponseErrorException(new ResponseError(CONNECTION_NOT_FOUND, "The provided configuration scope is bound to an unknown connection", configurationScopeId));
    }
    if ((connection instanceof SonarCloudConnectionConfiguration sonarCloudConnection)) {
      return new BindingWithOrg(sonarCloudConnection.getOrganization(), binding);
    }
    return new BindingWithOrg(null, binding);
  }

  private AiSuggestionRequestBodyDto toDto(@Nullable String organizationKey, String projectKey, RaisedIssue raisedIssue) {
    // this is not perfect, the file content might have changed since the issue was detected
    var clientFile = clientFileSystemService.getClientFile(raisedIssue.fileUri());
    if (clientFile == null) {
      throw new ResponseErrorException(new ResponseError(FILE_NOT_FOUND, "The provided issue ID corresponds to an unknown file", null));
    }
    var issue = raisedIssue.issueDto();
    // the text range presence was checked earlier
    var textRange = requireNonNull(issue.getTextRange());
    return new AiSuggestionRequestBodyDto(organizationKey, projectKey,
      new AiSuggestionRequestBodyDto.Issue(issue.getPrimaryMessage(), textRange.getStartLine(), textRange.getEndLine(), issue.getRuleKey(),
        clientFile.getContent()));
  }

  private AiSuggestionRequestBodyDto toDto(@Nullable String organizationKey, String projectKey, TaintVulnerabilityDto taint, String configScopeId) {
    ClientFile clientFile = null;
    var baseDir = clientFileSystemService.getBaseDir(configScopeId);
    if (baseDir != null) {
      var fileUri = baseDir.resolve(taint.getIdeFilePath()).toUri();
      clientFile = clientFileSystemService.getClientFile(fileUri);
    }
    if (clientFile == null) {
      throw new ResponseErrorException(new ResponseError(FILE_NOT_FOUND, "The provided taint ID corresponds to an unknown file", null));
    }
    // the text range presence was checked earlier
    var textRange = requireNonNull(taint.getTextRange());
    return new AiSuggestionRequestBodyDto(organizationKey, projectKey,
      new AiSuggestionRequestBodyDto.Issue(taint.getMessage(), textRange.getStartLine(), textRange.getEndLine(), taint.getRuleKey(), clientFile.getContent()));
  }

  private record BindingWithOrg(@Nullable String organizationKey, Binding binding) {
  }
}
