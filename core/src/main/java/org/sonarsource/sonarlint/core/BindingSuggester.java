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
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.client.api.util.TextSearchIndex;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.clientapi.config.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang.StringUtils.removeEnd;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sonarsource.sonarlint.core.commons.log.SonarLintLogger.singlePlural;
import static org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration.SONARCLOUD_URL;

public class BindingSuggester {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String SONAR_SCANNER_CONFIG_FILENAME = "sonar-project.properties";
  public static final String AUTOSCAN_CONFIG_FILENAME = ".sonarcloud.properties";

  private final ConfigurationRepository configRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarLintClient client;

  public BindingSuggester(ConfigurationRepository configRepository, ConnectionConfigurationRepository connectionRepository, SonarLintClient client) {
    this.configRepository = configRepository;
    this.connectionRepository = connectionRepository;
    this.client = client;
  }

  @Subscribe
  public void bindingConfigChanged(BindingConfigChangedEvent event) {
    // Check if binding suggestion was switched on
    if (!event.getNewConfig().isBindingSuggestionDisabled() && event.getPreviousConfig().isBindingSuggestionDisabled()) {
      suggestBindingForConfigScope(event.getNewConfig().getConfigScopeId());
    }
  }

  @Subscribe
  public void configurationScopeAdded(ConfigurationScopeAddedEvent event) {
    var configScopeId = event.getAddedConfigurationScopeId();
    var bindingConfiguration = configRepository.getBindingConfiguration(configScopeId);
    if (bindingConfiguration == null) {
      // Maybe the configuration was removed since the event was raised
      LOG.debug("Configuration scope '{}' not found. Ignoring event.", configScopeId);
      return;
    }
    if (!bindingConfiguration.isBindingSuggestionDisabled()) {
      suggestBindingForConfigScope(configScopeId);
    }
  }

  @Subscribe
  public void connectionAdded(ConnectionAddedEvent event) {
    // Double check if added connection has not been removed in the meantime
    if (connectionRepository.getConnectionById(event.getAddedConnectionId()) != null) {
      suggestBindingForAllScopes();
    }
  }

  private void suggestBindingForConfigScope(String configScopeId) {
    LOG.debug("Binding suggestion computation started for config scope '{}'...", configScopeId);
    if (noConnectionsConfigured()) {
      return;
    }
    if (!isScopeEligibleForBindingSuggestion(configScopeId)) {
      return;
    }
    var bindingSuggestions = suggestBindingForEligibleScope(configScopeId);
    client.suggestBinding(new SuggestBindingParams(Map.of(configScopeId, bindingSuggestions)));
  }

  private void suggestBindingForAllScopes() {
    LOG.debug("Binding suggestions computation started...");
    if (noConnectionsConfigured()) {
      return;
    }
    var allConfigScopeIds = configRepository.getConfigScopeIds();
    var eligibleConfigScopesForBindingSuggestion = new HashSet<String>();
    for (String configScopeId : allConfigScopeIds) {
      if (isScopeEligibleForBindingSuggestion(configScopeId)) {
        eligibleConfigScopesForBindingSuggestion.add(configScopeId);
      }
    }

    if (eligibleConfigScopesForBindingSuggestion.isEmpty()) {
      return;
    }

    Map<String, List<BindingSuggestionDto>> suggestions = new HashMap<>();

    eligibleConfigScopesForBindingSuggestion.forEach(configScopeId -> {
      var scopeSuggestions = suggestBindingForEligibleScope(configScopeId);
      LOG.debug("Found {} {} for configuration scope '{}'", scopeSuggestions.size(), singlePlural(scopeSuggestions.size(), "suggestion", "suggestions"), configScopeId);
      suggestions.put(configScopeId, scopeSuggestions);
    });

    client.suggestBinding(new SuggestBindingParams(suggestions));
  }

  private List<BindingSuggestionDto> suggestBindingForEligibleScope(String checkedConfigScopeId) {
    List<BindingClue> bindingClues = collectBindingClues(checkedConfigScopeId);
    List<BindingClueWithConnections> cluesAndConnections = matchConnections(bindingClues);

    List<BindingSuggestionDto> suggestions = new ArrayList<>();
    var cluesWithProjectKey = cluesAndConnections.stream().filter(c -> c.bindingClue.getSonarProjectKey() != null).collect(toList());
    for (BindingClueWithConnections bindingClueWithConnections : cluesWithProjectKey) {
      var sonarProjectKey = requireNonNull(bindingClueWithConnections.bindingClue.getSonarProjectKey());
      for (String connectionId : bindingClueWithConnections.connectionIds) {
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
        var cluesWithoutProjectKey = cluesAndConnections.stream().filter(c -> c.bindingClue.getSonarProjectKey() == null).collect(toList());
        for (BindingClueWithConnections bindingClueWithConnections : cluesWithoutProjectKey) {
          searchGoodMatchInConnections(suggestions, configScopeName, bindingClueWithConnections.connectionIds);
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
      LOG.debug("Attempt to find a good match for '{}' on connection '{}'...", configScopeName, connectionId);
      List<ServerProject> projects;
      try {
        projects = getServerApi(connectionId).map(s -> s.component().getAllProjects(new ProgressMonitor(null))).orElse(List.of());
      } catch (Exception e) {
        LOG.error("Error while querying projects from connection '{}'", connectionId, e);
        continue;
      }
      if (projects.isEmpty()) {
        LOG.debug("No projects for connection '{}'", connectionId);
      } else {
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

  @NotNull
  private List<BindingClueWithConnections> matchConnections(List<BindingClue> bindingClues) {
    LOG.debug("Match connections...");
    List<BindingClueWithConnections> cluesAndConnections = new ArrayList<>();
    for (BindingClue bindingClue : bindingClues) {
      var connectionsIds = matchConnections(bindingClue);
      if (!connectionsIds.isEmpty()) {
        cluesAndConnections.add(new BindingClueWithConnections(bindingClue, connectionsIds));
      }
    }
    LOG.debug("{} {} having at least one matching connection", cluesAndConnections.size(), singlePlural(cluesAndConnections.size(), "clue", "clues"));
    return cluesAndConnections;
  }

  private static class BindingClueWithConnections {
    private final BindingClue bindingClue;
    private final Set<String> connectionIds;

    private BindingClueWithConnections(BindingClue bindingClue, Set<String> connectionIds) {
      this.bindingClue = bindingClue;
      this.connectionIds = connectionIds;
    }
  }

  private List<BindingClue> collectBindingClues(String checkedConfigScopeId) {
    LOG.debug("Query client for binding clues...");
    FindFileByNamesInScopeResponse response;
    try {
      response = client.findFileByNamesInScope(new FindFileByNamesInScopeParams(checkedConfigScopeId, List.of(SONAR_SCANNER_CONFIG_FILENAME, AUTOSCAN_CONFIG_FILENAME))).get(1,
        TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (ExecutionException e) {
      LOG.error("Unable to search scanner clues", e.getCause());
      return List.of();
    } catch (TimeoutException e) {
      LOG.error("Unable to search scanner clues in time", e);
      return List.of();
    }

    List<BindingClue> bindingClues = new ArrayList<>();
    for (FoundFileDto foundFile : response.getFoundFiles()) {
      var scannerProps = extractScannerProperties(foundFile);
      if (scannerProps == null) {
        continue;
      }
      var bindingClue = computeBindingClue(foundFile.getFileName(), scannerProps);
      if (bindingClue != null) {
        bindingClues.add(bindingClue);
      }
    }
    LOG.debug("Found {} binding {}", bindingClues.size(), singlePlural(bindingClues.size(), "clue", "clues"));
    return bindingClues;
  }

  private Set<String> matchConnections(BindingClue bindingClue) {
    if (bindingClue instanceof SonarQubeBindingClue) {
      var serverUrl = ((SonarQubeBindingClue) bindingClue).serverUrl;
      return connectionRepository.getConnectionsById().values().stream()
        .filter(SonarQubeConnectionConfiguration.class::isInstance)
        .map(SonarQubeConnectionConfiguration.class::cast)
        .filter(c -> c.isSameServerUrl(serverUrl))
        .map(AbstractConnectionConfiguration::getConnectionId)
        .collect(toSet());
    }
    if (bindingClue instanceof SonarCloudBindingClue) {
      var organization = ((SonarCloudBindingClue) bindingClue).organization;
      return connectionRepository.getConnectionsById().values().stream()
        .filter(SonarCloudConnectionConfiguration.class::isInstance)
        .map(SonarCloudConnectionConfiguration.class::cast)
        .filter(c -> organization == null || Objects.equals(organization, c.getOrganization()))
        .map(AbstractConnectionConfiguration::getConnectionId)
        .collect(toSet());
    }
    return connectionRepository.getConnectionsById().keySet();
  }

  @CheckForNull
  private static ScannerProperties extractScannerProperties(FoundFileDto matchedFile) {
    LOG.debug("Extracting scanner properties from {}", matchedFile.getFilePath());
    var properties = new Properties();
    try {
      properties.load(new StringReader(matchedFile.getContent()));
    } catch (IOException e) {
      LOG.error("Unable to parse content of file '{}'", matchedFile.getFilePath(), e);
      return null;
    }
    return new ScannerProperties(getAndTrim(properties, "sonar.projectKey"), getAndTrim(properties, "sonar.organization"),
      getAndTrim(properties, "sonar.host.url"));
  }

  @CheckForNull
  private static String getAndTrim(Properties properties, String key) {
    return trimToNull(properties.getProperty(key));
  }

  private static class ScannerProperties {
    private final String projectKey;
    private final String organization;
    private final String serverUrl;

    private ScannerProperties(@Nullable String projectKey, @Nullable String organization, @Nullable String serverUrl) {
      this.projectKey = projectKey;
      this.organization = organization;
      this.serverUrl = serverUrl;
    }
  }

  @CheckForNull
  private static BindingClue computeBindingClue(String filename, ScannerProperties scannerProps) {
    if (AUTOSCAN_CONFIG_FILENAME.equals(filename)) {
      return new SonarCloudBindingClue(scannerProps.projectKey, scannerProps.organization);
    }
    if (scannerProps.organization != null) {
      return new SonarCloudBindingClue(scannerProps.projectKey, scannerProps.organization);
    }
    if (scannerProps.serverUrl != null) {
      if (removeEnd(scannerProps.serverUrl, "/").equals(SONARCLOUD_URL)) {
        return new SonarCloudBindingClue(scannerProps.projectKey, null);
      } else {
        return new SonarQubeBindingClue(scannerProps.projectKey, scannerProps.serverUrl);
      }
    }
    if (scannerProps.projectKey != null) {
      return new UnknownBindingClue(scannerProps.projectKey);
    }
    return null;
  }

  private interface BindingClue {

    @CheckForNull
    String getSonarProjectKey();

  }
  private static class UnknownBindingClue implements BindingClue {
    private final String sonarProjectKey;

    private UnknownBindingClue(String sonarProjectKey) {
      this.sonarProjectKey = sonarProjectKey;
    }

    @Override
    public String getSonarProjectKey() {
      return sonarProjectKey;
    }
  }

  private static class SonarQubeBindingClue implements BindingClue {

    private final String sonarProjectKey;
    private final String serverUrl;

    private SonarQubeBindingClue(@Nullable String sonarProjectKey, String serverUrl) {
      this.sonarProjectKey = sonarProjectKey;
      this.serverUrl = serverUrl;
    }

    @Override
    public String getSonarProjectKey() {
      return sonarProjectKey;
    }
  }

  private static class SonarCloudBindingClue implements BindingClue {

    private final String sonarProjectKey;
    private final String organization;

    private SonarCloudBindingClue(@Nullable String sonarProjectKey, @Nullable String organization) {
      this.sonarProjectKey = sonarProjectKey;
      this.organization = organization;
    }

    @Override
    public String getSonarProjectKey() {
      return sonarProjectKey;
    }
  }

  private boolean isScopeEligibleForBindingSuggestion(String configScopeId) {
    var bindingConfiguration = configRepository.getBindingConfiguration(configScopeId);
    if (bindingConfiguration == null) {
      // Race condition
      LOG.debug("Configuration scope '{}' is gone.", configScopeId);
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

  private boolean noConnectionsConfigured() {
    if (connectionRepository.getConnectionsById().isEmpty()) {
      LOG.debug("No connections defined");
      return true;
    }
    return false;
  }
}
