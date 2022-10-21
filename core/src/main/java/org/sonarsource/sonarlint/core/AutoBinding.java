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
import org.sonarsource.sonarlint.core.clientapi.config.binding.AutoBindCandidate;
import org.sonarsource.sonarlint.core.clientapi.config.binding.SuggestAutoBindParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.clientapi.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.fs.FoundFile;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionAddedEvent;
import org.sonarsource.sonarlint.core.referential.BindingConfiguration;
import org.sonarsource.sonarlint.core.referential.ConfigurationRepository;
import org.sonarsource.sonarlint.core.referential.ConfigurationScope;
import org.sonarsource.sonarlint.core.referential.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang.StringUtils.removeEnd;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class AutoBinding {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String SONAR_SCANNER_CONFIG_FILENAME = "sonar-project.properties";
  public static final String AUTOSCAN_CONFIG_FILENAME = ".sonarcloud.properties";
  private static final String SONARCLOUD_URL = "https://sonarcloud.io";

  private final ConfigurationRepository configRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarLintClient client;

  public AutoBinding(ConfigurationRepository configRepository, ConnectionConfigurationRepository connectionRepository, SonarLintClient client) {
    this.configRepository = configRepository;
    this.connectionRepository = connectionRepository;
    this.client = client;
  }

  @Subscribe
  public void bindingConfigChanged(BindingConfigChangedEvent event) {
    // Check if auto-bind is switched on
    if (event.getNewConfig().isAutoBindEnabled() && !event.getPreviousConfig().isAutoBindEnabled()) {
      autoBindConfigScope(event.getNewConfig().getConfigScopeId());
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
    if (bindingConfiguration.isAutoBindEnabled()) {
      autoBindConfigScope(configScopeId);
    }
  }

  @Subscribe
  public void connectionAdded(ConnectionAddedEvent event) {
    // Double check if added connection has not been removed in the meantime
    if (connectionRepository.getConnectionById(event.getAddedConnectionId()) != null) {
      autoBindAll();
    }
  }

  private void autoBindConfigScope(String configScopeId) {
    LOG.debug("Auto-binding started for config scope '{}'...", configScopeId);
    if (!checkAtLeastOneConnection()) {
      return;
    }
    if (!checkIfValidCandidateForAutoBinding(configScopeId)) {
      return;
    }
    var autoBindCandidates = autoBindAfterChecks(configScopeId);
    client.suggestAutoBind(new SuggestAutoBindParams(Map.of(configScopeId, autoBindCandidates)));
  }

  private void autoBindAll() {
    LOG.debug("Auto-binding started...");
    if (!checkAtLeastOneConnection()) {
      return;
    }
    var allConfigScopeIds = configRepository.getConfigScopeIds();
    var candidateConfigScopeForAutoBinding = new HashSet<String>();
    for (String configScopeId : allConfigScopeIds) {
      if (checkIfValidCandidateForAutoBinding(configScopeId)) {
        candidateConfigScopeForAutoBinding.add(configScopeId);
      }
    }

    if (candidateConfigScopeForAutoBinding.isEmpty()) {
      return;
    }

    Map<String, List<AutoBindCandidate>> candidates = new HashMap<>();

    candidateConfigScopeForAutoBinding.forEach(configScopeId -> {
      var autoBindCandidates = autoBindAfterChecks(configScopeId);
      candidates.put(configScopeId, autoBindCandidates);
    });

    client.suggestAutoBind(new SuggestAutoBindParams(candidates));
  }

  private List<AutoBindCandidate> autoBindAfterChecks(String checkedConfigScopeId) {
    List<BindingClue> bindingClues = collectBindingClues(checkedConfigScopeId);
    List<BindingClueWithConnections> cluesAndConnections = matchConnections(bindingClues);

    List<AutoBindCandidate> candidates = new ArrayList<>();
    var cluesWithProjectKey = cluesAndConnections.stream().filter(c -> c.bindingClue.getSonarProjectKey() != null).collect(toList());
    for (BindingClueWithConnections bindingClueWithConnections : cluesWithProjectKey) {
      var sonarProjectKey = bindingClueWithConnections.bindingClue.getSonarProjectKey();
      for (String connectionId : bindingClueWithConnections.connectionIds) {
        var project = getServerApi(connectionId).component().getProject(sonarProjectKey);
        project.ifPresent(serverProject -> candidates.add(new AutoBindCandidate(connectionId, sonarProjectKey, serverProject.getName())));
      }
    }
    if (candidates.isEmpty()) {
      var configScopeName = Optional.ofNullable(configRepository.getConfigurationScope(checkedConfigScopeId)).map(ConfigurationScope::getName).orElse(null);
      if (isNotBlank(configScopeName)) {
        var cluesWithoutProjectKey = cluesAndConnections.stream().filter(c -> c.bindingClue.getSonarProjectKey() == null).collect(toList());
        for (BindingClueWithConnections bindingClueWithConnections : cluesWithoutProjectKey) {
          searchGoodMatchInConnections(candidates, configScopeName, bindingClueWithConnections.connectionIds);
        }
        if (cluesWithoutProjectKey.isEmpty()) {
          searchGoodMatchInConnections(candidates, configScopeName, connectionRepository.getConnectionsById().keySet());
        }
      }
    }
    return candidates;
  }

  private void searchGoodMatchInConnections(List<AutoBindCandidate> candidates, String configScopeName, Set<String> connectionIdsToSearch) {
    for (String connectionId : connectionIdsToSearch) {
      var projects = getServerApi(connectionId).component().getAllProjects(new ProgressMonitor(null));
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
          candidates.add(new AutoBindCandidate(connectionId, serverProjectScoreEntry.getKey().getKey(), serverProjectScoreEntry.getKey().getName()));
        }
      }
    }
  }

  private ServerApi getServerApi(String connectionId) {
    var connectionConfig = connectionRepository.getConnectionById(connectionId);
    EndpointParams params;
    if (connectionConfig instanceof SonarQubeConnectionConfiguration) {
      params = new EndpointParams(((SonarQubeConnectionConfiguration) connectionConfig).getServerUrl(), false, null);
    } else {
      params = new EndpointParams(SONARCLOUD_URL, true, ((SonarCloudConnectionConfiguration) connectionConfig).getOrganization());
    }
    HttpClient httpClient = client.getHttpClient(connectionId);
    return new ServerApi(params, httpClient);
  }

  @NotNull
  private List<BindingClueWithConnections> matchConnections(List<BindingClue> bindingClues) {
    List<BindingClueWithConnections> cluesAndConnections = new ArrayList<>();
    for (BindingClue bindingClue : bindingClues) {
      var connectionsIds = matchConnections(bindingClue);
      if (!connectionsIds.isEmpty()) {
        cluesAndConnections.add(new BindingClueWithConnections(bindingClue, connectionsIds));
      }
    }
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
    for (FoundFile foundFile : response.getFoundFiles()) {
      var scannerProps = extractScannerProperties(foundFile);
      if (scannerProps == null) {
        continue;
      }
      var bindingClue = computeBindingClue(foundFile.getFilename(), scannerProps);
      if (bindingClue != null) {
        bindingClues.add(bindingClue);
      }
    }
    return bindingClues;
  }

  private Set<String> matchConnections(BindingClue bindingClue) {
    if (bindingClue instanceof SonarQubeBindingClue) {
      var serverUrl = ((SonarQubeBindingClue) bindingClue).serverUrl;
      return connectionRepository.getConnectionsById().values().stream()
        .filter(SonarQubeConnectionConfiguration.class::isInstance)
        .map(SonarQubeConnectionConfiguration.class::cast)
        .filter(c -> isSameServerUrl(c.getServerUrl(), serverUrl))
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

  private static boolean isSameServerUrl(String left, String right) {
    return Objects.equals(removeEnd(left, "/"), removeEnd(right, "/"));
  }

  @CheckForNull
  private static ScannerProperties extractScannerProperties(FoundFile matchedFile) {
    var properties = new Properties();
    try {
      properties.load(new StringReader(matchedFile.getContent()));
    } catch (IOException e) {
      LOG.error("Unable to parse content of file '{}'", matchedFile.getFilename(), e);
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

  private boolean checkIfValidCandidateForAutoBinding(String configScopeId) {
    var bindingConfiguration = configRepository.getBindingConfiguration(configScopeId);
    if (bindingConfiguration == null) {
      // Race condition
      LOG.debug("Configuration scope '{}' is gone. Skipping auto-binding", configScopeId);
      return false;
    }
    if (isBound(bindingConfiguration)) {
      LOG.debug("Configuration scope '{}' is already bound. Skipping.", configScopeId);
      return false;
    }
    if (!bindingConfiguration.isAutoBindEnabled()) {
      LOG.debug("Configuration scope '{}' has auto-bind disabled. Skipping.", configScopeId);
      return false;
    }
    return true;
  }

  private boolean isBound(BindingConfiguration bindingConfiguration) {
    return bindingConfiguration.getConnectionId() != null
      && bindingConfiguration.getSonarProjectKey() != null
      && connectionRepository.getConnectionById(bindingConfiguration.getConnectionId()) != null;
  }

  private boolean checkAtLeastOneConnection() {
    if (connectionRepository.getConnectionsById().isEmpty()) {
      LOG.debug("No connections defined");
      return false;
    }
    return true;
  }
}
