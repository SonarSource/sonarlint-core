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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.SonarLintException;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SharedConnectedModeSettingsProviderTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  public static final String ORGANIZATION = "myOrganization";
  public static final String SERVER_URL = "http://localhost:9000";
  private final ConnectionConfigurationRepository connectionConfigurationRepository = mock(ConnectionConfigurationRepository.class);
  private final ConfigurationRepository configurationRepository = mock(ConfigurationRepository.class);
  private final String SQ_CONNECTION_ID = "sqConnection";
  private final String SC_CONNECTION_ID = "scConnection";
  private final String PROJECT_KEY = "projectKey";
  private final BindingConfiguration SQ_BINDING = new BindingConfiguration(SQ_CONNECTION_ID, PROJECT_KEY, false);
  private final BindingConfiguration SC_BINDING = new BindingConfiguration(SC_CONNECTION_ID, PROJECT_KEY, false);
  private final SonarQubeConnectionConfiguration SQ_CONNECTION = new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID, SERVER_URL, false);
  private final SonarCloudConnectionConfiguration SC_CONNECTION = new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_URI, SC_CONNECTION_ID, ORGANIZATION, false);
  private final SharedConnectedModeSettingsProvider underTest = new SharedConnectedModeSettingsProvider(configurationRepository, connectionConfigurationRepository);
  private final Path resourcesDirectory = Paths.get("src", "test", "resources");

  @Test
  void should_generate_sonarqube_shared_connectedMode_file_contents() throws IOException {
    var configScopeId = "file:///path/to/my/folder";
    var sampleFileContent = Files.readString(Path.of(resourcesDirectory.toFile().getAbsolutePath(), "sampleSonarQubeSharedConnectedModeFile.json"), StandardCharsets.UTF_8);

    when(configurationRepository.getBindingConfiguration(configScopeId)).thenReturn(SQ_BINDING);
    when(connectionConfigurationRepository.getConnectionById(SQ_CONNECTION_ID)).thenReturn(SQ_CONNECTION);

    var jsonFileContent = underTest.getSharedConnectedModeConfigFileContents(configScopeId);
    assertThat(jsonFileContent).isEqualTo(sampleFileContent);
  }

  @Test
  void should_generate_sonarcloud_shared_connectedMode_file_contents() throws IOException {
    var configScopeId = "file:///path/to/my/folder";
    var sampleFileContent = Files.readString(Path.of(resourcesDirectory.toFile().getAbsolutePath(), "sampleSonarCloudSharedConnectedModeFile.json"), StandardCharsets.UTF_8);

    when(configurationRepository.getBindingConfiguration(configScopeId)).thenReturn(SC_BINDING);
    when(connectionConfigurationRepository.getConnectionById(SC_CONNECTION_ID)).thenReturn(SC_CONNECTION);

    var jsonFileContent = underTest.getSharedConnectedModeConfigFileContents(configScopeId);
    assertThat(jsonFileContent).isEqualTo(sampleFileContent);
  }

  @Test
  void should_log_and_throw_error_if_no_binding_found() {
    var configScopeId = "file:///path/to/my/folder";

    Exception exception = assertThrows(SonarLintException.class, () -> underTest.getSharedConnectedModeConfigFileContents(configScopeId));
    assertThat(logTester.logs(LogOutput.Level.WARN)).contains("Request for generating shared Connected Mode configuration file content failed; Binding not yet available for '" + configScopeId + "'");

    assertThat(exception.getMessage()).isEqualTo("Binding not found; Cannot generate shared Connected Mode file contents");
  }

  @Test
  void should_log_and_throw_error_if_binding_with_no_connectionId() {
    var configScopeId = "file:///path/to/my/folder";

    when(configurationRepository.getBindingConfiguration(configScopeId)).thenReturn(new BindingConfiguration(null, PROJECT_KEY, false));

    Exception exception = assertThrows(SonarLintException.class, () -> underTest.getSharedConnectedModeConfigFileContents(configScopeId));
    assertThat(logTester.logs(LogOutput.Level.WARN)).contains("Request for generating shared Connected Mode configuration file content failed; Binding not yet available for '" + configScopeId + "'");

    assertThat(exception.getMessage()).isEqualTo("Binding not found; Cannot generate shared Connected Mode file contents");
  }



}