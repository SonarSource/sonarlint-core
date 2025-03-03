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
package org.sonarsource.sonarlint.core.remediation.aicodefix;

import java.util.Optional;
import java.util.UUID;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.FixSuggestionReceivedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.reporting.PreviouslyRaisedFindingsRepository;
import org.sonarsource.sonarlint.core.repository.reporting.RaisedIssue;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixChangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;
import org.sonarsource.sonarlint.core.serverapi.fixsuggestions.AiSuggestionRequestBodyDto;
import org.sonarsource.sonarlint.core.serverapi.fixsuggestions.AiSuggestionResponseBodyDto;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Objects.requireNonNull;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_BOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONNECTION_KIND_NOT_SUPPORTED;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONNECTION_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.FILE_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.ISSUE_NOT_FOUND;

public class AiCodeFixService {
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionManager connectionManager;
  private final PreviouslyRaisedFindingsRepository previouslyRaisedFindingsRepository;
  private final ClientFileSystemService clientFileSystemService;
  private final StorageService storageService;
  private final ApplicationEventPublisher eventPublisher;

  public AiCodeFixService(ConnectionConfigurationRepository connectionRepository, ConfigurationRepository configurationRepository, ConnectionManager connectionManager,
    PreviouslyRaisedFindingsRepository previouslyRaisedFindingsRepository, ClientFileSystemService clientFileSystemService, StorageService storageService,
    ApplicationEventPublisher eventPublisher) {
    this.connectionRepository = connectionRepository;
    this.configurationRepository = configurationRepository;
    this.connectionManager = connectionManager;
    this.previouslyRaisedFindingsRepository = previouslyRaisedFindingsRepository;
    this.clientFileSystemService = clientFileSystemService;
    this.storageService = storageService;
    this.eventPublisher = eventPublisher;
  }

  public SuggestFixResponse suggestFix(String configurationScopeId, UUID issueId, SonarLintCancelMonitor cancelMonitor) {
    var sonarQubeCloudBinding = ensureBoundToSonarQubeCloud(configurationScopeId);
    var connection = connectionManager.getConnectionOrThrow(sonarQubeCloudBinding.binding().connectionId());
    var responseBodyDto = connection.withClientApiAndReturn(serverApi -> previouslyRaisedFindingsRepository.findRaisedIssueById(issueId)
      .map(issue -> {
        var aiCodeFixFeature = getFeature(sonarQubeCloudBinding.binding());
        if (!aiCodeFixFeature.map(feature -> feature.isFixable(issue)).orElse(false)) {
          throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "The provided issue cannot be fixed", issueId));
        }

        var fixResponseDto = serverApi.fixSuggestions().getAiSuggestion(toDto(sonarQubeCloudBinding.organizationKey, sonarQubeCloudBinding.binding().sonarProjectKey(), issue),
          cancelMonitor);

        eventPublisher.publishEvent(new FixSuggestionReceivedEvent(
          fixResponseDto.id().toString(),
          AiSuggestionSource.SONARCLOUD,
          fixResponseDto.changes().size(),
          // As of today, this is always true since suggestFix is only called by the clients
          true)
        );

        return fixResponseDto;
      })
      .orElseThrow(() -> new ResponseErrorException(new ResponseError(ISSUE_NOT_FOUND, "The provided issue does not exist", issueId))));
    return adapt(responseBodyDto);
  }

  public Optional<AiCodeFixFeature> getFeature(Binding binding) {
    return storageService.connection(binding.connectionId()).aiCodeFix().read()
      .filter(settings -> settings.isFeatureEnabled(binding.sonarProjectKey()))
      .map(AiCodeFixFeature::new);
  }

  private static SuggestFixResponse adapt(AiSuggestionResponseBodyDto responseBodyDto) {
    return new SuggestFixResponse(responseBodyDto.id(), responseBodyDto.explanation(),
      responseBodyDto.changes().stream().map(change -> new SuggestFixChangeDto(change.startLine(), change.endLine(), change.newCode())).toList());
  }

  private SonarQubeCloudBinding ensureBoundToSonarQubeCloud(String configurationScopeId) {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (effectiveBinding.isEmpty()) {
      throw new ResponseErrorException(new ResponseError(CONFIG_SCOPE_NOT_BOUND, "The provided configuration scope is not bound", configurationScopeId));
    }
    var binding = effectiveBinding.get();
    var connection = connectionRepository.getConnectionById(binding.connectionId());
    if (connection == null) {
      throw new ResponseErrorException(new ResponseError(CONNECTION_NOT_FOUND, "The provided configuration scope is bound to an unknown connection", configurationScopeId));
    }
    if (!(connection instanceof SonarCloudConnectionConfiguration sonarCloudConnection)) {
      throw new ResponseErrorException(new ResponseError(CONNECTION_KIND_NOT_SUPPORTED, "The provided configuration scope is not bound to SonarQube Cloud", null));
    }
    return new SonarQubeCloudBinding(sonarCloudConnection.getOrganization(), binding);
  }

  private AiSuggestionRequestBodyDto toDto(String organizationKey, String projectKey, RaisedIssue raisedIssue) {
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

  private record SonarQubeCloudBinding(String organizationKey, Binding binding) {
  }
}
