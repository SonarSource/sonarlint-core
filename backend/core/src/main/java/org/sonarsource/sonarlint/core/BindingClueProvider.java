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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang.StringUtils.removeEnd;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.sonarsource.sonarlint.core.commons.log.SonarLintLogger.singlePlural;

@Named
@Singleton
public class BindingClueProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String CLUES = "CLUES";
  private static final String SONAR_SCANNER_CONFIG_FILENAME = "sonar-project.properties";
  private static final String AUTOSCAN_CONFIG_FILENAME = ".sonarcloud.properties";

  static final Set<String> ALL_BINDING_CLUE_FILENAMES = Set.of(SONAR_SCANNER_CONFIG_FILENAME, AUTOSCAN_CONFIG_FILENAME);

  private final ConnectionConfigurationRepository connectionRepository;
  private final ClientFileSystemService clientFs;
  private final URI sonarCloudUri;

  public BindingClueProvider(ConnectionConfigurationRepository connectionRepository, ClientFileSystemService clientFs, SonarCloudActiveEnvironment sonarCloudActiveEnvironment) {
    this.connectionRepository = connectionRepository;
    this.clientFs = clientFs;
    this.sonarCloudUri = sonarCloudActiveEnvironment.getUri();
  }

  public List<BindingClueWithConnections> collectBindingCluesWithConnections(String configScopeId, Set<String> connectionIds, SonarLintCancelMonitor cancelMonitor) {
    var bindingClues = collectBindingClues(configScopeId, cancelMonitor);
    return matchConnections(bindingClues, connectionIds);
  }

  private List<BindingClueWithConnections> matchConnections(List<BindingClue> bindingClues, Set<String> eligibleConnectionIds) {
    LOG.debug("Match connections...");
    List<BindingClueWithConnections> cluesAndConnections = new ArrayList<>();
    for (var bindingClue : bindingClues) {
      var connectionsIds = matchConnections(bindingClue, eligibleConnectionIds);
      if (!connectionsIds.isEmpty()) {
        cluesAndConnections.add(new BindingClueWithConnections(bindingClue, connectionsIds));
      }
    }
    LOG.debug("{} {} having at least one matching connection", cluesAndConnections.size(), singlePlural(cluesAndConnections.size(), "clue", CLUES));
    return cluesAndConnections;
  }

  public static class BindingClueWithConnections {
    private final BindingClue bindingClue;
    private final Set<String> connectionIds;

    BindingClueWithConnections(BindingClue bindingClue, Set<String> connectionIds) {
      this.bindingClue = bindingClue;
      this.connectionIds = connectionIds;
    }

    public BindingClue getBindingClue() {
      return bindingClue;
    }

    public Set<String> getConnectionIds() {
      return connectionIds;
    }
  }

  public List<BindingClue> collectBindingClues(String checkedConfigScopeId, SonarLintCancelMonitor cancelMonitor) {
    var sonarlintConfigurationFiles = clientFs.findSonarlintConfigurationFilesByScope(checkedConfigScopeId);
    if (!sonarlintConfigurationFiles.isEmpty()) {
      var collectedClues = collectFromFiles(sonarlintConfigurationFiles, cancelMonitor);
      if (!collectedClues.isEmpty()) {
        LOG.debug("Found {} binding {} from SonarLint configuration files", collectedClues.size(), singlePlural(collectedClues.size(), "clue", CLUES));
        return collectedClues;
      }
    }

    var bindingCluesFiles = clientFs.findFilesByNamesInScope(checkedConfigScopeId, List.copyOf(ALL_BINDING_CLUE_FILENAMES));
    if (!bindingCluesFiles.isEmpty()) {
      var collectedClues = collectFromFiles(bindingCluesFiles, cancelMonitor);
      if (!collectedClues.isEmpty()) {
        LOG.debug("Found {} binding {}", collectedClues.size(), singlePlural(collectedClues.size(), "clue", CLUES));
        return collectedClues;
      }
    }

    LOG.debug("No binding clues were found");
    return Collections.emptyList();
  }

  private List<BindingClue> collectFromFiles(List<ClientFile> files, SonarLintCancelMonitor cancelMonitor) {
    var bindingClues = new ArrayList<BindingClue>();
    for (var foundFile : files) {
      cancelMonitor.checkCanceled();
      var scannerProps = extractConnectionProperties(foundFile);
      if (scannerProps == null) {
        continue;
      }
      var bindingClue = computeBindingClue(foundFile.getFileName(), scannerProps);
      if (bindingClue != null) {
        if (foundFile.isSonarlintConfigurationFile() && !(bindingClue instanceof UnknownBindingClue)) {
          LOG.debug("Found a SonarLint configuration file with a clue");
        }
        bindingClues.add(bindingClue);
      }
    }
    return bindingClues;
  }

  private Set<String> matchConnections(BindingClue bindingClue, Set<String> eligibleConnectionIds) {
    if (bindingClue instanceof SonarQubeBindingClue) {
      var serverUrl = ((SonarQubeBindingClue) bindingClue).serverUrl;
      return eligibleConnectionIds.stream().map(connectionRepository::getConnectionById)
        .filter(SonarQubeConnectionConfiguration.class::isInstance)
        .map(SonarQubeConnectionConfiguration.class::cast)
        .filter(c -> c.isSameServerUrl(serverUrl))
        .map(AbstractConnectionConfiguration::getConnectionId)
        .collect(toSet());
    }
    if (bindingClue instanceof SonarCloudBindingClue) {
      var organization = ((SonarCloudBindingClue) bindingClue).organization;
      return eligibleConnectionIds.stream().map(connectionRepository::getConnectionById)
        .filter(SonarCloudConnectionConfiguration.class::isInstance)
        .map(SonarCloudConnectionConfiguration.class::cast)
        .filter(c -> organization == null || Objects.equals(organization, c.getOrganization()))
        .map(AbstractConnectionConfiguration::getConnectionId)
        .collect(toSet());
    }
    return eligibleConnectionIds;
  }

  @CheckForNull
  private static ConnectionProperties  extractSonarLintConfiguration(ClientFile sonarLintConfigurationFile) {
    try {
      var configuration = new Gson().fromJson(sonarLintConfigurationFile.getContent(), JsonObject.class);
      var projectKey = configuration.get("projectKey");
      var organization = configuration.get("organization");
      var serverUrl = configuration.get("serverUrl");
      return new ConnectionProperties(projectKey != null ? projectKey.getAsString() : null,
        organization != null ? organization.getAsString() : null,
        serverUrl != null ? serverUrl.getAsString() : null);
    } catch (JsonSyntaxException e) {
      return null;
    }
  }

  @CheckForNull
  private static ConnectionProperties extractConnectionProperties(ClientFile matchedFile) {
    LOG.debug("Extracting scanner properties from {}", matchedFile);
    if (matchedFile.isSonarlintConfigurationFile()) {
      return extractSonarLintConfiguration(matchedFile);
    } else {
      var properties = new Properties();
      try {
        properties.load(new StringReader(matchedFile.getContent()));
      } catch (Exception e) {
        LOG.error("Unable to parse content of file '{}'", matchedFile, e);
        return null;
      }
      return new ConnectionProperties(getAndTrim(properties, "sonar.projectKey"), getAndTrim(properties, "sonar.organization"),
        getAndTrim(properties, "sonar.host.url"));
    }
  }

  @CheckForNull
  private static String getAndTrim(Properties properties, String key) {
    return trimToNull(properties.getProperty(key));
  }

  private static class ConnectionProperties {
    private final String projectKey;
    private final String organization;
    private final String serverUrl;

    private ConnectionProperties(@Nullable String projectKey, @Nullable String organization, @Nullable String serverUrl) {
      this.projectKey = projectKey;
      this.organization = organization;
      this.serverUrl = serverUrl;
    }
  }

  @CheckForNull
  private BindingClue computeBindingClue(String filename, ConnectionProperties scannerProps) {
    if (AUTOSCAN_CONFIG_FILENAME.equals(filename)) {
      return new SonarCloudBindingClue(scannerProps.projectKey, scannerProps.organization);
    }
    if (scannerProps.organization != null) {
      return new SonarCloudBindingClue(scannerProps.projectKey, scannerProps.organization);
    }
    if (scannerProps.serverUrl != null) {
      if (removeEnd(scannerProps.serverUrl, "/").equals(sonarCloudUri.toString())) {
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

  public interface BindingClue {

    @CheckForNull
    String getSonarProjectKey();

  }

  public static class UnknownBindingClue implements BindingClue {
    private final String sonarProjectKey;

    UnknownBindingClue(String sonarProjectKey) {
      this.sonarProjectKey = sonarProjectKey;
    }

    @Override
    public String getSonarProjectKey() {
      return sonarProjectKey;
    }
  }

  public static class SonarQubeBindingClue implements BindingClue {

    private final String sonarProjectKey;
    private final String serverUrl;

    SonarQubeBindingClue(@Nullable String sonarProjectKey, String serverUrl) {
      this.sonarProjectKey = sonarProjectKey;
      this.serverUrl = serverUrl;
    }

    @Override
    public String getSonarProjectKey() {
      return sonarProjectKey;
    }

    public String getServerUrl() {
      return serverUrl;
    }

  }

  public static class SonarCloudBindingClue implements BindingClue {

    private final String sonarProjectKey;
    private final String organization;

    SonarCloudBindingClue(@Nullable String sonarProjectKey, @Nullable String organization) {
      this.sonarProjectKey = sonarProjectKey;
      this.organization = organization;
    }

    @Override
    public String getSonarProjectKey() {
      return sonarProjectKey;
    }

    public String getOrganization() {
      return organization;
    }

  }

}
