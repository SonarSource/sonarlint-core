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

import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.FileSystemUpdatedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.springframework.context.event.EventListener;

import static java.lang.String.join;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sonarsource.sonarlint.core.commons.log.SonarLintLogger.singlePlural;

@Named
@Singleton
public class BindingSuggestionProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConfigurationRepository configRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarLintRpcClient client;
  private final BindingClueProvider bindingClueProvider;
  private final SonarProjectsCache sonarProjectsCache;
  private final ExecutorServiceShutdownWatchable<?> executorService;
  private final AtomicBoolean enabled = new AtomicBoolean(true);

  @Inject
  public BindingSuggestionProvider(ConfigurationRepository configRepository, ConnectionConfigurationRepository connectionRepository, SonarLintRpcClient client,
    BindingClueProvider bindingClueProvider, SonarProjectsCache sonarProjectsCache) {
    this.configRepository = configRepository;
    this.connectionRepository = connectionRepository;
    this.client = client;
    this.bindingClueProvider = bindingClueProvider;
    this.sonarProjectsCache = sonarProjectsCache;
    this.executorService = new ExecutorServiceShutdownWatchable<>(new ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(), r -> new Thread(r, "Binding Suggestion Provider")));
  }

  @EventListener
  public void bindingConfigChanged(BindingConfigChangedEvent event) {
    // Check if binding suggestion was switched on
    if (!event.getNewConfig().isBindingSuggestionDisabled() && event.getPreviousConfig().isBindingSuggestionDisabled()) {
      suggestBindingForGivenScopesAndAllConnections(Set.of(event.getConfigScopeId()));
    }
  }

  @EventListener
  public void configurationScopesAdded(ConfigurationScopesAddedEvent event) {
    var configScopeIds = event.getAddedConfigurationScopeIds();
    suggestBindingForGivenScopesAndAllConnections(configScopeIds);
  }

  private void suggestBindingForGivenScopesAndAllConnections(Set<String> configScopeIdsToSuggest) {
    if (!configScopeIdsToSuggest.isEmpty()) {
      var allConnectionIds = connectionRepository.getConnectionsById().keySet();
      if (allConnectionIds.isEmpty()) {
        LOG.debug("No connections configured, skipping binding suggestions.");
        return;
      }
      LOG.debug("Binding suggestion computation queued for config scopes '{}'...", join(",", configScopeIdsToSuggest));
      queueBindingSuggestionComputation(configScopeIdsToSuggest, allConnectionIds);
    }
  }

  @EventListener
  public void connectionAdded(ConnectionConfigurationAddedEvent event) {
    // Double check if added connection has not been removed in the meantime
    var addedConnectionId = event.getAddedConnectionId();
    var allConfigScopeIds = configRepository.getConfigScopeIds();
    if (connectionRepository.getConnectionById(addedConnectionId) != null && !allConfigScopeIds.isEmpty()) {
      LOG.debug("Binding suggestions computation queued for connection '{}'...", addedConnectionId);
      var candidateConnectionIds = Set.of(addedConnectionId);
      queueBindingSuggestionComputation(allConfigScopeIds, candidateConnectionIds);
    }
  }

  @EventListener
  public void filesystemUpdated(FileSystemUpdatedEvent event) {
    var configScopeWithAddedOrUpdatedBindingClue = event.getAddedOrUpdated().stream()
      .filter(file -> BindingClueProvider.ALL_BINDING_CLUE_FILENAMES.contains(file.getFileName()))
      .map(ClientFile::getConfigScopeId)
      .collect(Collectors.toSet());
    suggestBindingForGivenScopesAndAllConnections(configScopeWithAddedOrUpdatedBindingClue);
  }

  public Map<String, List<BindingSuggestionDto>> getBindingSuggestions(String configScopeId, String connectionId, SonarLintCancelMonitor cancelMonitor) {
    return computeBindingSuggestions(Set.of(configScopeId), Set.of(connectionId), cancelMonitor);
  }

  private void queueBindingSuggestionComputation(Set<String> configScopeIds, Set<String> candidateConnectionIds) {
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(executorService);
    executorService.submit(() -> {
      if (enabled.get()) {
        computeAndNotifyBindingSuggestions(configScopeIds, candidateConnectionIds, cancelMonitor);
      } else {
        LOG.debug("Skipping binding suggestion computation as it is disabled");
      }
    });
  }

  private void computeAndNotifyBindingSuggestions(Set<String> configScopeIds, Set<String> candidateConnectionIds, SonarLintCancelMonitor cancelMonitor) {
    Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope = computeBindingSuggestions(configScopeIds, candidateConnectionIds, cancelMonitor);
    if (!suggestionsByConfigScope.isEmpty()) {
      client.suggestBinding(new SuggestBindingParams(suggestionsByConfigScope));
    }
  }

  private Map<String, List<BindingSuggestionDto>> computeBindingSuggestions(Set<String> configScopeIds, Set<String> candidateConnectionIds, SonarLintCancelMonitor cancelMonitor) {
    var eligibleConfigScopesForBindingSuggestion = new HashSet<String>();
    for (var configScopeId : configScopeIds) {
      cancelMonitor.checkCanceled();
      if (isScopeEligibleForBindingSuggestion(configScopeId)) {
        eligibleConfigScopesForBindingSuggestion.add(configScopeId);
      }
    }

    if (eligibleConfigScopesForBindingSuggestion.isEmpty()) {
      return emptyMap();
    }

    Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope = new HashMap<>();

    for (var configScopeId : eligibleConfigScopesForBindingSuggestion) {
      cancelMonitor.checkCanceled();
      var scopeSuggestions = suggestBindingForEligibleScope(configScopeId, candidateConnectionIds, cancelMonitor);
      LOG.debug("Found {} {} for configuration scope '{}'", scopeSuggestions.size(), singlePlural(scopeSuggestions.size(), "suggestion", "suggestions"), configScopeId);
      suggestionsByConfigScope.put(configScopeId, scopeSuggestions);
    }

    return suggestionsByConfigScope;
  }

  private List<BindingSuggestionDto> suggestBindingForEligibleScope(String checkedConfigScopeId, Set<String> candidateConnectionIds, SonarLintCancelMonitor cancelMonitor) {
    var cluesAndConnections = bindingClueProvider.collectBindingCluesWithConnections(checkedConfigScopeId, candidateConnectionIds, cancelMonitor);

    List<BindingSuggestionDto> suggestions = new ArrayList<>();
    var cluesWithProjectKey = cluesAndConnections.stream().filter(c -> c.getBindingClue().getSonarProjectKey() != null).collect(toList());
    for (var bindingClueWithConnections : cluesWithProjectKey) {
      var sonarProjectKey = requireNonNull(bindingClueWithConnections.getBindingClue().getSonarProjectKey());
      for (var connectionId : bindingClueWithConnections.getConnectionIds()) {
        sonarProjectsCache
          .getSonarProject(connectionId, sonarProjectKey, cancelMonitor)
          .ifPresent(serverProject -> suggestions.add(new BindingSuggestionDto(connectionId, sonarProjectKey, serverProject.getName())));
      }
    }
    if (suggestions.isEmpty()) {
      var configScopeName = Optional.ofNullable(configRepository.getConfigurationScope(checkedConfigScopeId)).map(ConfigurationScope::getName).orElse(null);
      if (isNotBlank(configScopeName)) {
        var cluesWithoutProjectKey = cluesAndConnections.stream().filter(c -> c.getBindingClue().getSonarProjectKey() == null).collect(toList());
        for (var bindingClueWithConnections : cluesWithoutProjectKey) {
          searchGoodMatchInConnections(suggestions, configScopeName, bindingClueWithConnections.getConnectionIds(), cancelMonitor);
        }
        if (cluesWithoutProjectKey.isEmpty()) {
          searchGoodMatchInConnections(suggestions, configScopeName, candidateConnectionIds, cancelMonitor);
        }
      }
    }
    return suggestions;
  }

  private void searchGoodMatchInConnections(List<BindingSuggestionDto> suggestions, String configScopeName, Set<String> connectionIdsToSearch,
    SonarLintCancelMonitor cancelMonitor) {
    for (var connectionId : connectionIdsToSearch) {
      searchGoodMatchInConnection(suggestions, configScopeName, connectionId, cancelMonitor);
    }
  }

  private void searchGoodMatchInConnection(List<BindingSuggestionDto> suggestions, String configScopeName, String connectionId, SonarLintCancelMonitor cancelMonitor) {
    LOG.debug("Attempt to find a good match for '{}' on connection '{}'...", configScopeName, connectionId);
    var index = sonarProjectsCache.getTextSearchIndex(connectionId, cancelMonitor);
    var searchResult = index.search(configScopeName);
    if (!searchResult.isEmpty()) {
      Double bestScore = Double.MIN_VALUE;
      for (var serverProjectScoreEntry : searchResult.entrySet()) {
        if (serverProjectScoreEntry.getValue() < bestScore) {
          break;
        }
        bestScore = serverProjectScoreEntry.getValue();
        suggestions.add(new BindingSuggestionDto(connectionId, serverProjectScoreEntry.getKey().getKey(), serverProjectScoreEntry.getKey().getName()));
      }
      LOG.debug("Best score = {}", String.format(Locale.ENGLISH, "%,.2f", bestScore));
    }
  }

  private boolean isScopeEligibleForBindingSuggestion(String configScopeId) {
    var configScope = configRepository.getConfigurationScope(configScopeId);
    var bindingConfiguration = configRepository.getBindingConfiguration(configScopeId);
    if (configScope == null || bindingConfiguration == null) {
      // Race condition
      LOG.debug("Configuration scope '{}' is gone.", configScopeId);
      return false;
    }
    if (!configScope.isBindable()) {
      LOG.debug("Configuration scope '{}' is not bindable.", configScopeId);
      return false;
    }
    if (isValidBinding(bindingConfiguration)) {
      LOG.debug("Configuration scope '{}' is already bound.", configScopeId);
      return false;
    }
    if (bindingConfiguration.isBindingSuggestionDisabled()) {
      LOG.debug("Configuration scope '{}' has binding suggestions disabled.", configScopeId);
      return false;
    }
    return true;
  }

  private boolean isValidBinding(BindingConfiguration bindingConfiguration) {
    return bindingConfiguration.ifBound((connectionId, projectKey) -> connectionRepository.getConnectionById(connectionId) != null)
      .orElse(false);
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop binding suggestions executor service in a timely manner");
    }
  }

  public void disable() {
    this.enabled.set(false);
  }

  public void enable() {
    this.enabled.set(true);
  }
}
