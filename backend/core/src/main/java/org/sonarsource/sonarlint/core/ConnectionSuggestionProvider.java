/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
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
import org.springframework.context.event.EventListener;

import static org.apache.commons.lang.StringUtils.removeEnd;
import static org.sonarsource.sonarlint.core.BindingClueProvider.ALL_BINDING_CLUE_FILENAMES;

@Named
@Singleton
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
    this.executorService = new ExecutorServiceShutdownWatchable<>(new ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(), r -> new Thread(r, "Connection Suggestion Provider")));
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
  public void configurationScopesAdded(ConfigurationScopesAddedEvent event) {
    var listConfigScopeIds = event.getAddedConfigurationScopeIds().stream()
      .map(clientFs::getFiles)
      .flatMap(List::stream)
      .filter(f -> ALL_BINDING_CLUE_FILENAMES.contains(f.getFileName()) || f.isSonarlintConfigurationFile())
      .map(ClientFile::getConfigScopeId)
      .collect(Collectors.toSet());

    if (!listConfigScopeIds.isEmpty()) {
      queueConnectionSuggestion(listConfigScopeIds);
    } else {
      bindingSuggestionProvider.suggestBindingForGivenScopesAndAllConnections(event.getAddedConfigurationScopeIds());
    }
  }

  private void queueConnectionSuggestion(Set<String> listConfigScopeIds) {
    if (!listConfigScopeIds.isEmpty()) {
      var cancelMonitor = new SonarLintCancelMonitor();
      cancelMonitor.watchForShutdown(executorService);
      executorService.submit(() -> suggestConnectionForGivenScopes(listConfigScopeIds, cancelMonitor));
    }
  }

  private void suggestConnectionForGivenScopes(Set<String> listOfFilesPerConfigScopeIds, SonarLintCancelMonitor cancelMonitor) {
    LOG.debug("Computing connection suggestions");
    var connectionSuggestionsByConfigScopeIds = new HashMap<String, List<ConnectionSuggestionDto>>();
    var bindingSuggestionsForConfigScopeIds = new HashSet<String>();
    
    for (var configScopeId : listOfFilesPerConfigScopeIds) {
      var effectiveBinding = configRepository.getEffectiveBinding(configScopeId);
      if (effectiveBinding.isPresent()) {
        LOG.debug("A binding already exists, skipping the connection suggestion");
        continue;
      }

      var bindingClues = bindingClueProvider.collectBindingClues(configScopeId, cancelMonitor);
      for (var bindingClue : bindingClues) {
        var projectKey = bindingClue.getSonarProjectKey();
        if (projectKey != null) {
          var result = handleBindingClue(bindingClue);
          if (result == null) {
            bindingSuggestionsForConfigScopeIds.add(configScopeId);
          } else if (result.isLeft()) {
            connectionSuggestionsByConfigScopeIds.computeIfAbsent(configScopeId, s -> new ArrayList<>())
              .add(new ConnectionSuggestionDto(new SonarQubeConnectionSuggestionDto(result.getLeft(), projectKey)));
          } else {
            connectionSuggestionsByConfigScopeIds.computeIfAbsent(configScopeId, s -> new ArrayList<>())
              .add(new ConnectionSuggestionDto(new SonarCloudConnectionSuggestionDto(result.getRight(), projectKey)));
          }
        }
      }
    }

    suggestConnectionToClientIfAny(connectionSuggestionsByConfigScopeIds, cancelMonitor);
    computeBindingSuggestionfAny(bindingSuggestionsForConfigScopeIds);
  }

  @CheckForNull
  private Either<String, String> handleBindingClue(BindingClueProvider.BindingClue bindingClue) {
    if (bindingClue instanceof BindingClueProvider.SonarCloudBindingClue) {
      LOG.debug("Found a SonarCloud binding clue");
      var organization = ((BindingClueProvider.SonarCloudBindingClue) bindingClue).getOrganization();
      var connection = connectionRepository.findByOrganization(organization);
      if (connection.isEmpty()) {
        return Either.forRight(organization);
      }
    } else if (bindingClue instanceof BindingClueProvider.SonarQubeBindingClue) {
      LOG.debug("Found a SonarQube binding clue");
      var serverUrl = ((BindingClueProvider.SonarQubeBindingClue) bindingClue).getServerUrl();
      var connection = connectionRepository.findByUrl(serverUrl);
      if (connection.isEmpty()) {
        return Either.forLeft(removeEnd(serverUrl, "/"));
      }
    } else {
      LOG.debug("Found an invalid binding clue for connection suggestion");
    }
    return null;
  }

  private void suggestConnectionToClientIfAny(Map<String, List<ConnectionSuggestionDto>> connectionSuggestionsByConfigScopeIds,
    SonarLintCancelMonitor cancelMonitor) {
    if (!connectionSuggestionsByConfigScopeIds.isEmpty()) {
      LOG.debug("Found {} connection suggestion(s)", connectionSuggestionsByConfigScopeIds.size());
      try {
        bindingSuggestionProvider.disable();
        var future = client.suggestConnection(new SuggestConnectionParams(connectionSuggestionsByConfigScopeIds));
        cancelMonitor.onCancel(() -> future.cancel(true));
        future.join();
      } finally {
        bindingSuggestionProvider.enable();
      }
    }
  }

  private void computeBindingSuggestionfAny(Set<String> bindingSuggestionsForConfigScopeIds) {
    if (!bindingSuggestionsForConfigScopeIds.isEmpty()) {
      LOG.debug("Found binding suggestion(s) for %s configuration scope IDs", bindingSuggestionsForConfigScopeIds.size());
      bindingSuggestionProvider.suggestBindingForGivenScopesAndAllConnections(bindingSuggestionsForConfigScopeIds);
    }
  }

}
