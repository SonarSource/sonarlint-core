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
package org.sonarsource.sonarlint.core;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileSystemUpdatedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarQubeConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SuggestConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.BindingClueProvider.ALL_BINDING_CLUE_FILENAMES;

public class ConnectionSuggestionProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConfigurationRepository configRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarLintRpcClient client;
  private final BindingClueProvider bindingClueProvider;
  private final ExecutorServiceShutdownWatchable<?> executorService;
  private final BindingSuggestionProvider bindingSuggestionProvider;
  private final ClientFileSystemService clientFs;

  @Inject
  public ConnectionSuggestionProvider(ConfigurationRepository configRepository, ConnectionConfigurationRepository connectionRepository, SonarLintRpcClient client,
    BindingClueProvider bindingClueProvider, BindingSuggestionProvider bindingSuggestionProvider, ClientFileSystemService clientFs) {
    this.configRepository = configRepository;
    this.connectionRepository = connectionRepository;
    this.client = client;
    this.bindingClueProvider = bindingClueProvider;
    this.executorService = new ExecutorServiceShutdownWatchable<>(FailSafeExecutors.newSingleThreadExecutor("Connection Suggestion Provider"));
    this.bindingSuggestionProvider = bindingSuggestionProvider;
    this.clientFs = clientFs;
  }

  @EventListener
  public void filesystemUpdated(FileSystemUpdatedEvent event) {
    var listConfigScopeIds = event.getAddedOrUpdated().stream()
      .filter(f -> ALL_BINDING_CLUE_FILENAMES.contains(f.getFileName()) || f.isSonarlintConfigurationFile())
      .map(ClientFile::getConfigScopeId)
      .collect(Collectors.toSet());

    queueConnectionSuggestion(listConfigScopeIds);
  }

  @EventListener
  public void configurationScopesAdded(ConfigurationScopesAddedWithBindingEvent event) {
    var listConfigScopeIds = event.getConfigScopeIds().stream()
      .map(clientFs::getFiles)
      .flatMap(List::stream)
      .filter(f -> ALL_BINDING_CLUE_FILENAMES.contains(f.getFileName()) || f.isSonarlintConfigurationFile())
      .map(ClientFile::getConfigScopeId)
      .collect(Collectors.toSet());

    if (!listConfigScopeIds.isEmpty()) {
      queueConnectionSuggestion(listConfigScopeIds);
    } else {
      bindingSuggestionProvider.suggestBindingForGivenScopesAndAllConnections(event.getConfigScopeIds());
    }
  }

  private void queueConnectionSuggestion(Set<String> listConfigScopeIds) {
    if (!listConfigScopeIds.isEmpty()) {
      var cancelMonitor = new SonarLintCancelMonitor();
      cancelMonitor.watchForShutdown(executorService);
      executorService.execute(() -> suggestConnectionAndBindingForGivenScopes(listConfigScopeIds, cancelMonitor));
    }
  }

  private void suggestConnectionAndBindingForGivenScopes(Set<String> configScopeIds, SonarLintCancelMonitor cancelMonitor) {
    var connectionAndBindingSuggestions = computeConnectionAndBindingSuggestions(configScopeIds, cancelMonitor);

    suggestConnectionToClientIfAny(connectionAndBindingSuggestions.connectionSuggestionsByConfigScopeIds());
    computeBindingSuggestionIfAny(connectionAndBindingSuggestions.bindingSuggestionsForConfigScopeIds());
  }

  private @NotNull ConnectionAndBindingSuggestions computeConnectionAndBindingSuggestions(Set<String> configScopeIds, SonarLintCancelMonitor cancelMonitor) {
    LOG.debug("Computing connection suggestions");
    var connectionSuggestionsByConfigScopeIds = new HashMap<String, List<ConnectionSuggestionDto>>();
    var bindingSuggestionsForConfigScopeIds = new HashSet<String>();

    for (var configScopeId : configScopeIds) {
      var effectiveBinding = configRepository.getEffectiveBinding(configScopeId);
      if (effectiveBinding.isPresent()) {
        LOG.debug("A binding already exists, skipping the connection suggestion");
        continue;
      }

      var bindingClues = bindingClueProvider.collectBindingClues(configScopeId, cancelMonitor);
      for (var bindingClue : bindingClues) {
        var projectKey = bindingClue.getSonarProjectKey();
        if (projectKey != null) {
          handleBindingClue(bindingClue).ifPresentOrElse(clue -> clue.map(
            serverUrl -> connectionSuggestionsByConfigScopeIds.computeIfAbsent(configScopeId, s -> new ArrayList<>())
              .add(new ConnectionSuggestionDto(new SonarQubeConnectionSuggestionDto(serverUrl, projectKey), bindingClue.getOrigin())),
            organization -> connectionSuggestionsByConfigScopeIds.computeIfAbsent(configScopeId, s -> new ArrayList<>())
              .add(new ConnectionSuggestionDto(new SonarCloudConnectionSuggestionDto(organization, projectKey,
                ((BindingClueProvider.SonarCloudBindingClue) bindingClue).getRegion()), bindingClue.getOrigin()))),
            () -> bindingSuggestionsForConfigScopeIds.add(configScopeId));
        }
      }
    }
    return new ConnectionAndBindingSuggestions(connectionSuggestionsByConfigScopeIds, bindingSuggestionsForConfigScopeIds);
  }

  private Optional<Either<String, String>> handleBindingClue(BindingClueProvider.BindingClue bindingClue) {
    if (bindingClue instanceof BindingClueProvider.SonarCloudBindingClue sonarCloudBindingClue) {
      LOG.debug("Found a SonarCloud binding clue");
      var organization = sonarCloudBindingClue.getOrganization();
      var connection = connectionRepository.findByOrganization(organization);
      if (connection.isEmpty()) {
        return Optional.of(Either.forRight(organization));
      }
    } else if (bindingClue instanceof BindingClueProvider.SonarQubeBindingClue sonarQubeBindingClue) {
      LOG.debug("Found a SonarQube binding clue");
      var serverUrl = sonarQubeBindingClue.getServerUrl();
      var connection = connectionRepository.findByUrl(serverUrl);
      if (connection.isEmpty()) {
        return Optional.of(Either.forLeft(Strings.CS.removeEnd(serverUrl, "/")));
      }
    } else {
      LOG.debug("Found an invalid binding clue for connection suggestion");
    }
    return Optional.empty();
  }

  private void suggestConnectionToClientIfAny(Map<String, List<ConnectionSuggestionDto>> connectionSuggestionsByConfigScopeIds) {
    if (!connectionSuggestionsByConfigScopeIds.isEmpty()) {
      var foundSuggestionsCount = connectionSuggestionsByConfigScopeIds.size();
      LOG.debug("Found {} connection {}", foundSuggestionsCount, SonarLintLogger.singlePlural(foundSuggestionsCount, "suggestion"));
      client.suggestConnection(new SuggestConnectionParams(connectionSuggestionsByConfigScopeIds));
    }
  }

  private void computeBindingSuggestionIfAny(Set<String> bindingSuggestionsForConfigScopeIds) {
    if (!bindingSuggestionsForConfigScopeIds.isEmpty()) {
      LOG.debug("Found binding suggestion(s) for %s configuration scope IDs", bindingSuggestionsForConfigScopeIds.size());
      bindingSuggestionProvider.suggestBindingForGivenScopesAndAllConnections(bindingSuggestionsForConfigScopeIds);
    }
  }

  public List<ConnectionSuggestionDto> getConnectionSuggestions(String configScopeId, SonarLintCancelMonitor cancelMonitor) {
    var connectionAndBindingSuggestions = computeConnectionAndBindingSuggestions(Set.of(configScopeId), cancelMonitor);
    return connectionAndBindingSuggestions.connectionSuggestionsByConfigScopeIds.containsKey(configScopeId) ?
      connectionAndBindingSuggestions.connectionSuggestionsByConfigScopeIds.get(configScopeId) :
      List.of();
  }

  private record ConnectionAndBindingSuggestions(
    Map<String, List<ConnectionSuggestionDto>> connectionSuggestionsByConfigScopeIds,
    Set<String> bindingSuggestionsForConfigScopeIds
  ) {}

}
