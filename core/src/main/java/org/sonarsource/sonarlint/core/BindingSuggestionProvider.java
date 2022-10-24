/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sonarsource.sonarlint.core.BindingClueProvider.BindingClueWithConnections;
import org.sonarsource.sonarlint.core.client.api.util.TextSearchIndex;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.clientapi.config.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static java.lang.String.join;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sonarsource.sonarlint.core.commons.log.SonarLintLogger.singlePlural;
import static org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration.SONARCLOUD_URL;

public class BindingSuggestionProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConfigurationRepository configRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarLintClient client;
  private final BindingClueProvider bindingClueProvider;

  public BindingSuggestionProvider(ConfigurationRepository configRepository, ConnectionConfigurationRepository connectionRepository, SonarLintClient client,
    BindingClueProvider bindingClueProvider) {
    this.configRepository = configRepository;
    this.connectionRepository = connectionRepository;
    this.client = client;
    this.bindingClueProvider = bindingClueProvider;
  }

  @Subscribe
  public void bindingConfigChanged(BindingConfigChangedEvent event) {
    // Check if binding suggestion was switched on
    if (!event.getNewConfig().isBindingSuggestionDisabled() && event.getPreviousConfig().isBindingSuggestionDisabled()) {
      suggestBindingForGivenScopesAndAllConnections(Set.of(event.getNewConfig().getConfigScopeId()));
    }
  }

  @Subscribe
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
      LOG.debug("Binding suggestion computation started for config scopes '{}'...", join(",", configScopeIdsToSuggest));
      suggestBindingForScopesAndConnections(configScopeIdsToSuggest, allConnectionIds);
    }
  }

  @Subscribe
  public void connectionAdded(ConnectionAddedEvent event) {
    // Double check if added connection has not been removed in the meantime
    var addedConnectionId = event.getAddedConnectionId();
    if (connectionRepository.getConnectionById(addedConnectionId) != null) {
      LOG.debug("Binding suggestions computation started for connection '{}'...", addedConnectionId);
      var allConfigScopeIds = configRepository.getConfigScopeIds();
      var eligibleConnectionIds = Set.of(addedConnectionId);
      suggestBindingForScopesAndConnections(allConfigScopeIds, eligibleConnectionIds);
    }
  }

  private void suggestBindingForScopesAndConnections(Set<String> configScopeIds, Set<String> eligibleConnectionIds) {
    var eligibleConfigScopesForBindingSuggestion = new HashSet<String>();
    for (String configScopeId : configScopeIds) {
      if (isScopeEligibleForBindingSuggestion(configScopeId)) {
        eligibleConfigScopesForBindingSuggestion.add(configScopeId);
      }
    }

    if (eligibleConfigScopesForBindingSuggestion.isEmpty()) {
      return;
    }

    Map<String, List<BindingSuggestionDto>> suggestions = new HashMap<>();

    eligibleConfigScopesForBindingSuggestion.forEach(configScopeId -> {
      var scopeSuggestions = suggestBindingForEligibleScope(configScopeId, eligibleConnectionIds);
      LOG.debug("Found {} {} for configuration scope '{}'", scopeSuggestions.size(), singlePlural(scopeSuggestions.size(), "suggestion", "suggestions"), configScopeId);
      suggestions.put(configScopeId, scopeSuggestions);
    });

    client.suggestBinding(new SuggestBindingParams(suggestions));
  }

  private List<BindingSuggestionDto> suggestBindingForEligibleScope(String checkedConfigScopeId, Set<String> eligibleConnectionIds) {
    var cluesAndConnections = bindingClueProvider.collectBindingCluesWithConnections(checkedConfigScopeId, eligibleConnectionIds);

    List<BindingSuggestionDto> suggestions = new ArrayList<>();
    var cluesWithProjectKey = cluesAndConnections.stream().filter(c -> c.getBindingClue().getSonarProjectKey() != null).collect(toList());
    for (BindingClueWithConnections bindingClueWithConnections : cluesWithProjectKey) {
      var sonarProjectKey = requireNonNull(bindingClueWithConnections.getBindingClue().getSonarProjectKey());
      for (String connectionId : bindingClueWithConnections.getConnectionIds()) {
        LOG.debug("Query if project '{}' exists on connection '{}'...", sonarProjectKey, connectionId);
        Optional<ServerProject> project;
        try {
          project = getServerApi(connectionId).flatMap(s -> s.component().getProject(sonarProjectKey));
        } catch (Exception e) {
          LOG.error("Error while querying project '{}' from connection '{}'", sonarProjectKey, connectionId, e);
          continue;
        }
        project.ifPresent(serverProject -> suggestions.add(new BindingSuggestionDto(connectionId, sonarProjectKey, serverProject.getName())));
      }
    }
    if (suggestions.isEmpty()) {
      var configScopeName = Optional.ofNullable(configRepository.getConfigurationScope(checkedConfigScopeId)).map(ConfigurationScope::getName).orElse(null);
      if (isNotBlank(configScopeName)) {
        var cluesWithoutProjectKey = cluesAndConnections.stream().filter(c -> c.getBindingClue().getSonarProjectKey() == null).collect(toList());
        for (BindingClueWithConnections bindingClueWithConnections : cluesWithoutProjectKey) {
          searchGoodMatchInConnections(suggestions, configScopeName, bindingClueWithConnections.getConnectionIds());
        }
        if (cluesWithoutProjectKey.isEmpty()) {
          searchGoodMatchInConnections(suggestions, configScopeName, connectionRepository.getConnectionsById().keySet());
        }
      }
    }
    return suggestions;
  }

  private void searchGoodMatchInConnections(List<BindingSuggestionDto> suggestions, String configScopeName, Set<String> connectionIdsToSearch) {
    for (String connectionId : connectionIdsToSearch) {
      searchGoodMatchInConnection(suggestions, configScopeName, connectionId);
    }
  }

  private void searchGoodMatchInConnection(List<BindingSuggestionDto> suggestions, String configScopeName, String connectionId) {
    LOG.debug("Attempt to find a good match for '{}' on connection '{}'...", configScopeName, connectionId);
    List<ServerProject> projects;
    try {
      projects = getServerApi(connectionId).map(s -> s.component().getAllProjects(new ProgressMonitor(null))).orElse(List.of());
    } catch (Exception e) {
      LOG.error("Error while querying projects from connection '{}'", connectionId, e);
      return;
    }
    if (projects.isEmpty()) {
      LOG.debug("No projects found for connection '{}'", connectionId);
    } else {
      searchGoodMatchAmongSonarProjects(suggestions, configScopeName, connectionId, projects);
    }
  }

  private static void searchGoodMatchAmongSonarProjects(List<BindingSuggestionDto> suggestions, String configScopeName, String connectionId, List<ServerProject> projects) {
    LOG.debug("Creating index for {} {}", projects.size(), singlePlural(projects.size(), "project", "projects"));
    var index = new TextSearchIndex<ServerProject>();
    projects.forEach(p -> index.index(p, p.getKey() + " " + p.getName()));
    var searchResult = index.search(configScopeName);
    if (!searchResult.isEmpty()) {
      Double bestScore = Double.MIN_VALUE;
      for (Map.Entry<ServerProject, Double> serverProjectScoreEntry : searchResult.entrySet()) {
        if (serverProjectScoreEntry.getValue() < bestScore) {
          break;
        }
        bestScore = serverProjectScoreEntry.getValue();
        suggestions.add(new BindingSuggestionDto(connectionId, serverProjectScoreEntry.getKey().getKey(), serverProjectScoreEntry.getKey().getName()));
      }
      LOG.debug("Best score = {}", bestScore);
    }
  }

  private Optional<ServerApi> getServerApi(String connectionId) {
    var connectionConfig = connectionRepository.getConnectionById(connectionId);
    EndpointParams params;
    if (connectionConfig instanceof SonarQubeConnectionConfiguration) {
      params = new EndpointParams(((SonarQubeConnectionConfiguration) connectionConfig).getServerUrl(), false, null);
    } else if (connectionConfig instanceof SonarCloudConnectionConfiguration) {
      params = new EndpointParams(SONARCLOUD_URL, true, ((SonarCloudConnectionConfiguration) connectionConfig).getOrganization());
    } else {
      throw new IllegalStateException("Unknown connection type");
    }
    var httpClient = client.getHttpClient(connectionId);
    if (httpClient != null) {
      return Optional.of(new ServerApi(params, httpClient));
    } else {
      return Optional.empty();
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

}
