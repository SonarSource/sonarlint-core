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

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingClueProviderTests {

  public static final String SQ_CONNECTION_ID_1 = "sq1";
  public static final String SQ_CONNECTION_ID_2 = "sq2";
  public static final String SC_CONNECTION_ID_1 = "sc1";
  public static final String SC_CONNECTION_ID_2 = "sc2";
  private static final String PROJECT_KEY_1 = "myproject1";
  public static final String MY_ORG_1 = "myOrg1";
  public static final String MY_ORG_2 = "myOrg2";

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  public static final String CONFIG_SCOPE_ID = "configScopeId";
  private final ConnectionConfigurationRepository connectionRepository = mock(ConnectionConfigurationRepository.class);
  private final SonarLintClient client = mock(SonarLintClient.class);
  BindingClueProvider underTest = new BindingClueProvider(connectionRepository, client);

  @Test
  void should_ask_client_for_scanner_files() throws InterruptedException {
    mockFindFileByNamesInScope(List.of());

    underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of());

    ArgumentCaptor<FindFileByNamesInScopeParams> argumentCaptor = ArgumentCaptor.forClass(FindFileByNamesInScopeParams.class);
    verify(client).findFileByNamesInScope(argumentCaptor.capture());
    var params = argumentCaptor.getValue();
    assertThat(params.getConfigScopeId()).isEqualTo(CONFIG_SCOPE_ID);
    assertThat(params.getFilenames()).containsExactlyInAnyOrder("sonar-project.properties", ".sonarcloud.properties");
  }

  @Test
  void should_log_and_ignore_error_when_asking_client_for_scanner_files() throws InterruptedException {
    when(client.findFileByNamesInScope(any())).thenReturn(CompletableFuture.failedFuture(new Exception("Error cause")));

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org"));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1));

    assertThat(bindingClueWithConnections).isEmpty();
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).contains("Unable to search scanner clues: Error cause");
  }

  @Test
  void should_log_and_ignore_timeout_when_asking_client_for_scanner_files() throws InterruptedException, ExecutionException, TimeoutException {
    var completableFutureWillTimeout = mock(CompletableFuture.class);
    when(completableFutureWillTimeout.get(anyLong(), any())).thenThrow(new TimeoutException());
    when(client.findFileByNamesInScope(any())).thenReturn(completableFutureWillTimeout);

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org"));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1));

    assertThat(bindingClueWithConnections).isEmpty();
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).contains("Unable to search scanner clues in time");
  }

  @Test
  void should_detect_sonar_scanner_for_sonarqube() throws InterruptedException {
    mockFindFileByNamesInScope(List.of(new FoundFileDto("sonar-project.properties", "path/to/sonar-project.properties", "sonar.host.url=http://mysonarqube.org\n")));

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org"));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1));

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.SonarQubeBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isNull();
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SQ_CONNECTION_ID_1);
  }

  @Test
  void should_detect_sonar_scanner_for_sonarqube_with_project_key() throws InterruptedException {
    mockFindFileByNamesInScope(
      List.of(new FoundFileDto("sonar-project.properties", "path/to/sonar-project.properties", "sonar.host.url=http://mysonarqube.org\nsonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org"));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1));

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isEqualTo(PROJECT_KEY_1);
  }

  @Test
  void should_match_multiple_connections() throws InterruptedException {
    mockFindFileByNamesInScope(List.of(new FoundFileDto("sonar-project.properties", "path/to/sonar-project.properties", "sonar.host.url=http://mysonarqube.org\n")));

    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org"));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_2)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_2, "http://Mysonarqube.org/"));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SQ_CONNECTION_ID_1, SQ_CONNECTION_ID_2));

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SQ_CONNECTION_ID_1, SQ_CONNECTION_ID_2);
  }

  @Test
  void should_detect_sonar_scanner_for_sonarcloud_based_on_url() throws InterruptedException {
    mockFindFileByNamesInScope(
      List.of(new FoundFileDto("sonar-project.properties", "path/to/sonar-project.properties", "sonar.host.url=https://sonarcloud.io\nsonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SC_CONNECTION_ID_1, MY_ORG_1));
    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_2)).thenReturn(new SonarCloudConnectionConfiguration(SC_CONNECTION_ID_2, MY_ORG_2));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SC_CONNECTION_ID_2));

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.SonarCloudBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SC_CONNECTION_ID_1, SC_CONNECTION_ID_2);
  }

  @Test
  void should_detect_sonar_scanner_for_sonarcloud_based_on_organization() throws InterruptedException {
    mockFindFileByNamesInScope(List.of(new FoundFileDto("sonar-project.properties", "path/to/sonar-project.properties", "sonar.organization=" + MY_ORG_2)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SC_CONNECTION_ID_1, MY_ORG_1));
    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_2)).thenReturn(new SonarCloudConnectionConfiguration(SC_CONNECTION_ID_2, MY_ORG_2));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SC_CONNECTION_ID_2));

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.SonarCloudBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isNull();
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SC_CONNECTION_ID_2);
  }

  @Test
  void should_detect_autoscan_for_sonarcloud() throws InterruptedException {
    mockFindFileByNamesInScope(List.of(new FoundFileDto(".sonarcloud.properties", "path/to/.sonarcloud.properties", "sonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SC_CONNECTION_ID_1, MY_ORG_1));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org"));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1));

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.SonarCloudBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SC_CONNECTION_ID_1);
  }

  @Test
  void should_detect_unknown_with_project_key() throws InterruptedException {
    mockFindFileByNamesInScope(List.of(new FoundFileDto("sonar-project.properties", "path/to/sonar-project.properties", "sonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SC_CONNECTION_ID_1, MY_ORG_1));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org"));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1));

    assertThat(bindingClueWithConnections).hasSize(1);
    var bindingClueWithConnections1 = bindingClueWithConnections.get(0);
    assertThat(bindingClueWithConnections1.getBindingClue()).isInstanceOf(BindingClueProvider.UnknownBindingClue.class);
    assertThat(bindingClueWithConnections1.getBindingClue().getSonarProjectKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(bindingClueWithConnections1.getConnectionIds()).containsOnly(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1);
  }

  @Test
  void ignore_scanner_file_without_clue() throws InterruptedException {
    mockFindFileByNamesInScope(List.of(new FoundFileDto("sonar-project.properties", "path/to/sonar-project.properties", "sonar.sources=src")));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SC_CONNECTION_ID_1, MY_ORG_1));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org"));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1));

    assertThat(bindingClueWithConnections).isEmpty();
  }

  @Test
  void ignore_scanner_file_invalid_content() throws InterruptedException {
    mockFindFileByNamesInScope(List.of(new FoundFileDto("sonar-project.properties", "path/to/sonar-project.properties", "\\usonar.projectKey=" + PROJECT_KEY_1)));

    when(connectionRepository.getConnectionById(SC_CONNECTION_ID_1)).thenReturn(new SonarCloudConnectionConfiguration(SC_CONNECTION_ID_1, MY_ORG_1));
    when(connectionRepository.getConnectionById(SQ_CONNECTION_ID_1)).thenReturn(new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_1, "http://mysonarqube.org"));

    var bindingClueWithConnections = underTest.collectBindingCluesWithConnections(CONFIG_SCOPE_ID, Set.of(SC_CONNECTION_ID_1, SQ_CONNECTION_ID_1));

    assertThat(bindingClueWithConnections).isEmpty();
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).contains("Unable to parse content of file 'path/to/sonar-project.properties'");
  }

  private void mockFindFileByNamesInScope(List<FoundFileDto> dtos) {
    when(client.findFileByNamesInScope(any())).thenReturn(CompletableFuture.completedFuture(new FindFileByNamesInScopeResponse(dtos)));
  }

}
