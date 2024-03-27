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

import java.util.Objects;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.SonarLintException;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;

import static java.lang.String.format;

@Named
@Singleton
public class SharedConnectedModeSettingsProvider {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private static final String SONARCLOUD_CONNECTED_MODE_CONFIG = "{\n" +
    "    \"sonarCloudOrganization\": \"%s\",\n" +
    "    \"projectKey\": \"%s\"\n" +
    "}";
  private static final String SONARQUBE_CONNECTED_MODE_CONFIG = "{\n" +
    "    \"sonarQubeUri\": \"%s\",\n" +
    "    \"projectKey\": \"%s\"\n" +
    "}";

  public SharedConnectedModeSettingsProvider(ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionRepository) {
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
  }

  public String getSharedConnectedModeConfigFileContents(String configScopeId) {
    var bindingConfiguration = configurationRepository.getBindingConfiguration(configScopeId);
    if (bindingConfiguration != null && bindingConfiguration.isBound()) {
      var projectKey = bindingConfiguration.getSonarProjectKey();
      var connectionId = bindingConfiguration.getConnectionId();

      var connection =  Objects.requireNonNull(connectionRepository.getConnectionById(Objects.requireNonNull(connectionId)));
      if (connection.getKind() == ConnectionKind.SONARCLOUD) {
        var organization = ((SonarCloudConnectionConfiguration) connection).getOrganization();

        return format(SONARCLOUD_CONNECTED_MODE_CONFIG, organization, projectKey);
      } else {
        return format(SONARQUBE_CONNECTED_MODE_CONFIG, connection.getEndpointParams().getBaseUrl(), projectKey);
      }
    } else {
      LOG.warn("Request for generating shared Connected Mode configuration file content failed; Binding not yet available for '{}'", configScopeId);
      throw new SonarLintException(format("Binding not found for '%s'; Cannot generate shared Connected Mode file contents", configScopeId));
    }
  }
}
