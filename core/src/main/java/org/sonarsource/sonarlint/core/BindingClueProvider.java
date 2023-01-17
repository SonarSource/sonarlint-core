/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang.StringUtils.removeEnd;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.sonarsource.sonarlint.core.commons.log.SonarLintLogger.singlePlural;
import static org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration.SONARCLOUD_URL;

public class BindingClueProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SONAR_SCANNER_CONFIG_FILENAME = "sonar-project.properties";
  private static final String AUTOSCAN_CONFIG_FILENAME = ".sonarcloud.properties";

  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarLintClient client;

  public BindingClueProvider(ConnectionConfigurationRepository connectionRepository, SonarLintClient client) {
    this.connectionRepository = connectionRepository;
    this.client = client;
  }

  public List<BindingClueWithConnections> collectBindingCluesWithConnections(String configScopeId, Set<String> connectionIds) throws InterruptedException {
    var bindingClues = collectBindingClues(configScopeId);
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
    LOG.debug("{} {} having at least one matching connection", cluesAndConnections.size(), singlePlural(cluesAndConnections.size(), "clue", "clues"));
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

  private List<BindingClue> collectBindingClues(String checkedConfigScopeId) throws InterruptedException {
    LOG.debug("Query client for binding clues...");
    FindFileByNamesInScopeResponse response;
    try {
      response = client.findFileByNamesInScope(new FindFileByNamesInScopeParams(checkedConfigScopeId, List.of(SONAR_SCANNER_CONFIG_FILENAME, AUTOSCAN_CONFIG_FILENAME))).get(1,
        TimeUnit.MINUTES);
    } catch (ExecutionException e) {
      LOG.error("Unable to search scanner clues: " + e.getCause().getMessage(), e.getCause());
      return List.of();
    } catch (TimeoutException e) {
      LOG.error("Unable to search scanner clues in time", e);
      return List.of();
    }

    List<BindingClue> bindingClues = new ArrayList<>();
    for (var foundFile : response.getFoundFiles()) {
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
  private static ScannerProperties extractScannerProperties(FoundFileDto matchedFile) {
    LOG.debug("Extracting scanner properties from {}", matchedFile.getFilePath());
    var properties = new Properties();
    try {
      properties.load(new StringReader(matchedFile.getContent()));
    } catch (Exception e) {
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

    private SonarQubeBindingClue(@Nullable String sonarProjectKey, String serverUrl) {
      this.sonarProjectKey = sonarProjectKey;
      this.serverUrl = serverUrl;
    }

    @Override
    public String getSonarProjectKey() {
      return sonarProjectKey;
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
  }

}
