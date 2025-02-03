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

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BindingClueProviderTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  public static final String SQ_CONNECTION_ID_1 = "sq1";
  public static final String SQ_CONNECTION_ID_2 = "sq2";
  public static final String SC_CONNECTION_ID_1 = "sc1";
  public static final String SC_CONNECTION_ID_2 = "sc2";
  private static final String PROJECT_KEY_1 = "myproject1";
  public static final String MY_ORG_1 = "myOrg1";
  public static final String MY_ORG_2 = "myOrg2";

  public static final String CONFIG_SCOPE_ID = "configScopeId";
  private final ConnectionConfigurationRepository connectionRepository = mock(ConnectionConfigurationRepository.class);
  private final ClientFileSystemService clientFs = mock(ClientFileSystemService.class);
  BindingClueProvider underTest = new BindingClueProvider(connectionRepository, clientFs, SonarCloudActiveEnvironment.prodEu());

  @Test
  void should_detect_sonar_scanner_for_sonarqube() {
    mockFindFileByNamesInScope(List.of(buildClientFile("sonar-project.properties", "path/to/sonar-project.properties", "sonar.host.url=http://mysonarqube.org\n")));

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.SonarQubeBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isNull();
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SQ_CONNECTION_ID_1);
  }

  @Test
  void should_detect_sonar_scanner_for_sonarqube_with_project_key() {
    mockFindFileByNamesInScope(
      List.of(buildClientFile("sonar-project.properties", "path/to/sonar-project.properties", "sonar.host.url=http://mysonarqube.org\nsonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isEqualTo(PROJECT_KEY_1);
  }

  @Test
  void should_match_multiple_connections() {
    mockFindFileByNamesInScope(List.of(buildClientFile("sonar-project.properties", "path/to/sonar-project.properties", "sonar.host.url=http://mysonarqube.org\n")));

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org", true));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_2)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_2, "http://Mysonarqube.org/", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1, SQ_CONNECTION_ID_2), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SQ_CONNECTION_ID_1, SQ_CONNECTION_ID_2);
  }

  @Test
  void should_detect_sonar_scanner_for_sonarcloud_based_on_url() {
    mockFindFileByNamesInScope(
      List.of(buildClientFile("sonar-project.properties", "path/to/sonar-project.properties", "sonar.host.url=https://sonarcloud.io\nsonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_EU_URI, SC_CONNECTION_ID_1, MY_ORG_1, "EU", true));
    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_2)).thenReturn(new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_EU_URI, SC_CONNECTION_ID_2, MY_ORG_2, "EU", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SC_CONNECTION_ID_2), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.SonarCloudBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SC_CONNECTION_ID_1, SC_CONNECTION_ID_2);
  }

  @Test
  void should_detect_sonar_scanner_for_sonarcloud_based_on_organization() {
    mockFindFileByNamesInScope(List.of(buildClientFile("sonar-project.properties", "path/to/sonar-project.properties", "sonar.organization=" + MY_ORG_2)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_EU_URI, SC_CONNECTION_ID_1, MY_ORG_1, "EU", true));
    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_2)).thenReturn(new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_EU_URI, SC_CONNECTION_ID_2, MY_ORG_2, "EU", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SC_CONNECTION_ID_2), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.SonarCloudBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isNull();
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SC_CONNECTION_ID_2);
  }

  @Test
  void should_detect_autoscan_for_sonarcloud() {
    mockFindFileByNamesInScope(List.of(buildClientFile(".sonarcloud.properties", "path/to/.sonarcloud.properties", "sonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_EU_URI, SC_CONNECTION_ID_1, MY_ORG_1, "EU", true));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.SonarCloudBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SC_CONNECTION_ID_1);
  }

  @Test
  void should_detect_unknown_with_project_key() {
    mockFindFileByNamesInScope(List.of(buildClientFile("sonar-project.properties", "path/to/sonar-project.properties", "sonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_EU_URI, SC_CONNECTION_ID_1, MY_ORG_1, "EU", true));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.UnknownBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1);
  }

  @Test
  void ignore_scanner_file_without_clue() {
    mockFindFileByNamesInScope(List.of(buildClientFile("sonar-project.properties", "path/to/sonar-project.properties", "sonar.sources=src")));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_EU_URI, SC_CONNECTION_ID_1, MY_ORG_1, "EU", true));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).isEmpty();
  }

  @Test
  void ignore_scanner_file_invalid_content() {
    mockFindFileByNamesInScope(List.of(buildClientFile("sonar-project.properties", "path/to/sonar-project.properties", "\\usonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_EU_URI, SC_CONNECTION_ID_1, MY_ORG_1, "EU", true));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).isEmpty();
    assertThat(logTester.logs(LogOutput.Level.ERROR)).contains("Unable to parse content of file 'file://path/to/sonar-project.properties'");
  }

  @Test
  void should_not_detect_sonarlint_configuration_file_if_wrong_content() {
    mockFindSonarlintConfigurationFilesByScope(List.of(buildClientFile("connectedMode.json", "/path/to/.sonarlint/connectedMode.json", "{\"sonarCloudOrganization\": \"org\",\"sonarQubeUri\": \"http://mysonarqube.org\"}")));

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).isEmpty();
  }

  @Test
  void should_not_detect_sonarlint_configuration_file_if_not_in_right_folder() {
    mockFindSonarlintConfigurationFilesByScope(List.of(buildClientFile("connectedMode.json", "/path/to/connections/connectedMode.json", "{\"projectKey\": \"pKey\",\"sonarQubeUri\": \"http://mysonarqube.org\"}")));

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org", true));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1), new SonarLintCancelMonitor());

    assertThat(bindingClueWithConnections).isEmpty();
  }

  @Test
  void should_not_detect_sonarlint_configuration_file_if_not_json() {
    var file = new ClientFile(URI.create("/path/to/.sonarlint/connectedMode.txt"), CONFIG_SCOPE_ID, Path.of("/path/to/.sonarlint/connectedMode.txt"), false, null, null, null, true);

    assertThat(file.isSonarlintConfigurationFile()).isFalse();
  }

  @Test
  void should_not_detect_sonarlint_configuration_file_if_wrong_folder() {
    var file = new ClientFile(URI.create("/path/to/.sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, Path.of("/path/to/.sonarlint2/connectedMode.json"), false, null, null, null, true);

    assertThat(file.isSonarlintConfigurationFile()).isFalse();
  }

  private ClientFile buildClientFile(String filename, String relativePath, String content) {
    var file = new ClientFile(URI.create("file://" + relativePath), CONFIG_SCOPE_ID, Paths.get(relativePath), false, null, null, null, true);
    file.setDirty(content);
    return file;
  }

  private void mockFindFileByNamesInScope(List<ClientFile> files) {
    when(clientFs.findFilesByNamesInScope(any(), any())).thenReturn(files);
  }

  private void mockFindSonarlintConfigurationFilesByScope(List<ClientFile> files) {
    when(clientFs.findSonarlintConfigurationFilesByScope(any())).thenReturn(files);
  }

}
