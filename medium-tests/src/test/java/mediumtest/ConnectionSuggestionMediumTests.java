/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class ConnectionSuggestionMediumTests {

  public static final String CONFIG_SCOPE_ID = "myProject1";
  public static final String SLCORE_PROJECT_KEY = "org.sonarsource.sonarlint:sonarlint-core-parent";
  public static final String ORGANIZATION = "Org";

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private SonarLintRpcServer backend;

  @AfterEach
  void stop() throws ExecutionException, InterruptedException, TimeoutException {
    backend.shutdown().get(5, TimeUnit.SECONDS);
  }

  @Test
  void should_suggest_sonarqube_connection_when_initializing_fs(@TempDir Path tmp) throws IOException {
    var sonarlintDir = tmp.resolve(".sonarlint/");
    Files.createDirectory(sonarlintDir);
    var clue = tmp.resolve(".sonarlint/connectedMode.json");
    Files.writeString(clue, "{\"projectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"sonarQubeUri\": \"" + sonarqubeMock.baseUrl() + "\"}", StandardCharsets.UTF_8);
    var fileDto = new ClientFileDto(clue.toUri(), Paths.get(".sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(fileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(Collections.emptyList(), List.of(fileDto)));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(1);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft()).isNotNull();
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getServerUrl()).isEqualTo(sonarqubeMock.baseUrl());
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isTrue();
  }

  @Test
  void should_suggest_sonarcloud_connection_when_initializing_fs(@TempDir Path tmp) throws IOException {
    var sonarlintDir = tmp.resolve(".sonarlint/");
    Files.createDirectory(sonarlintDir);
    var clue = tmp.resolve(".sonarlint/connectedMode.json");
    Files.writeString(clue, "{\"projectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"sonarCloudOrganization\": \"" + ORGANIZATION + "\"}", StandardCharsets.UTF_8);
    var fileDto = new ClientFileDto(clue.toUri(), Paths.get(".sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(fileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(Collections.emptyList(), List.of(fileDto)));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(1);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getRight()).isNotNull();
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getRight().getOrganization()).isEqualTo(ORGANIZATION);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getRight().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isTrue();
  }

  @Test
  void should_suggest_connection_when_initializing_fs_for_csharp_project(@TempDir Path tmp) throws IOException {
    var sonarlintDir = tmp.resolve("random");
    Files.createDirectory(sonarlintDir);
    sonarlintDir = tmp.resolve("random/path");
    Files.createDirectory(sonarlintDir);
    sonarlintDir = tmp.resolve("random/path/.sonarlint");
    Files.createDirectory(sonarlintDir);
    var clue = tmp.resolve("random/path/.sonarlint/random_name.json");
    Files.writeString(clue, "{\"projectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"sonarQubeUri\": \"" + sonarqubeMock.baseUrl() + "\"}", StandardCharsets.UTF_8);
    var fileDto = new ClientFileDto(clue.toUri(), Paths.get("random/path/.sonarlint/random_name.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(fileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(Collections.emptyList(), List.of(fileDto)));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(1);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft()).isNotNull();
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getServerUrl()).isEqualTo(sonarqubeMock.baseUrl());
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isTrue();
  }

  @Test
  void should_suggest_connection_when_initializing_fs_with_scanner_file(@TempDir Path tmp) throws IOException {
    var clue = tmp.resolve("sonar-project.properties");
    Files.writeString(clue, "sonar.host.url=" + sonarqubeMock.baseUrl() + "\nsonar.projectKey=" + SLCORE_PROJECT_KEY, StandardCharsets.UTF_8);
    var fileDto = new ClientFileDto(clue.toUri(), Paths.get("sonar-project.properties"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(fileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(Collections.emptyList(), List.of(fileDto)));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(1);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft()).isNotNull();
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getServerUrl()).isEqualTo(sonarqubeMock.baseUrl());
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isFalse();
  }

  @Test
  void should_suggest_sonarcloud_connection_when_initializing_fs_with_scanner_file(@TempDir Path tmp) throws IOException {
    var clue = tmp.resolve(".sonarcloud.properties");
    Files.writeString(clue, "sonar.organization=" + ORGANIZATION + "\nsonar.projectKey=" + SLCORE_PROJECT_KEY, StandardCharsets.UTF_8);
    var fileDto = new ClientFileDto(clue.toUri(), Paths.get("sonar-project.properties"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(fileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(Collections.emptyList(), List.of(fileDto)));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(1);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getRight()).isNotNull();
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getRight().getOrganization()).isEqualTo(ORGANIZATION);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getRight().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isFalse();
  }

  @Test
  void should_suggest_connection_when_config_scope_added(@TempDir Path tmp) throws IOException {
    var sonarlintDir = tmp.resolve(".sonarlint/");
    Files.createDirectory(sonarlintDir);
    var clue = tmp.resolve(".sonarlint/connectedMode.json");
    Files.writeString(clue, "{\"projectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"sonarQubeUri\": \"" + sonarqubeMock.baseUrl() + "\"}", StandardCharsets.UTF_8);
    var fileDto = new ClientFileDto(clue.toUri(), Paths.get(".sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(fileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(1);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft()).isNotNull();
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getServerUrl()).isEqualTo(sonarqubeMock.baseUrl());
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isTrue();
  }

  @Test
  void should_suggest_connection_with_multiple_bindings_when_config_scope_added(@TempDir Path tmp) throws IOException {
    var sonarlintDir = tmp.resolve(".sonarlint/");
    Files.createDirectory(sonarlintDir);
    var sqClue = tmp.resolve(".sonarlint/connectedMode1.json");
    Files.writeString(sqClue, "{\"projectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"sonarQubeUri\": \"" + sonarqubeMock.baseUrl() + "\"}", StandardCharsets.UTF_8);
    var scClue = tmp.resolve(".sonarlint/connectedMode2.json");
    Files.writeString(scClue, "{\"projectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"sonarCloudOrganization\": \"" + ORGANIZATION + "\"}", StandardCharsets.UTF_8);
    var sqFileDto = new ClientFileDto(sqClue.toUri(), Paths.get(".sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), sqClue, null);
    var scFileDto = new ClientFileDto(scClue.toUri(), Paths.get(".sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), scClue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(sqFileDto, scFileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(2);
    for (var suggestion : connectionSuggestion.get(CONFIG_SCOPE_ID)) {
      if (suggestion.getConnectionSuggestion().isLeft()) {
        assertThat(suggestion.getConnectionSuggestion().getLeft().getServerUrl()).isEqualTo(sonarqubeMock.baseUrl());
        assertThat(suggestion.getConnectionSuggestion().getLeft().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
      } else {
        assertThat(suggestion.getConnectionSuggestion().getRight().getOrganization()).isEqualTo(ORGANIZATION);
        assertThat(suggestion.getConnectionSuggestion().getRight().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
      }
      assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isTrue();
    }
  }

  @Test
  void should_suggest_sonarlint_configuration_in_priority(@TempDir Path tmp) throws IOException {
    var sonarlintDir = tmp.resolve(".sonarlint/");
    Files.createDirectory(sonarlintDir);
    var sqClue = tmp.resolve(".sonarlint/connectedMode1.json");
    Files.writeString(sqClue, "{\"projectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"sonarQubeUri\": \"" + sonarqubeMock.baseUrl() + "\"}", StandardCharsets.UTF_8);
    var propertyClue = tmp.resolve("sonar-project.properties");
    Files.writeString(propertyClue, "sonar.host.url=https://sonarcloud.io\nsonar.projectKey=", StandardCharsets.UTF_8);
    var sqFileDto = new ClientFileDto(sqClue.toUri(), Paths.get(".sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), sqClue, null);
    var scFileDto = new ClientFileDto(propertyClue.toUri(), Paths.get("sonar-project.properties"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), propertyClue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(sqFileDto, scFileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(1);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft()).isNotNull();
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getServerUrl()).isEqualTo(sonarqubeMock.baseUrl());
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isTrue();
  }

  @Test
  void should_suggest_sonarqube_connection_when_pascal_case(@TempDir Path tmp) throws IOException {
    var sonarlintDir = tmp.resolve(".sonarlint/");
    Files.createDirectory(sonarlintDir);
    var clue = tmp.resolve(".sonarlint/connectedMode.json");
    Files.writeString(clue, "{\"ProjectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"SonarQubeUri\": \"" + sonarqubeMock.baseUrl() + "\"}", StandardCharsets.UTF_8);
    var fileDto = new ClientFileDto(clue.toUri(), Paths.get(".sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(fileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(Collections.emptyList(), List.of(fileDto)));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(1);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft()).isNotNull();
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getServerUrl()).isEqualTo(sonarqubeMock.baseUrl());
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getLeft().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isTrue();
  }

  @Test
  void should_suggest_sonarcloud_connection_when_pascal_case(@TempDir Path tmp) throws IOException {
    var sonarlintDir = tmp.resolve(".sonarlint/");
    Files.createDirectory(sonarlintDir);
    var clue = tmp.resolve(".sonarlint/connectedMode.json");
    Files.writeString(clue, "{\"ProjectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"SonarCloudOrganization\": \"" + ORGANIZATION + "\"}", StandardCharsets.UTF_8);
    var fileDto = new ClientFileDto(clue.toUri(), Paths.get(".sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null);
    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(fileDto))
      .build();

    backend = newBackend()
      .build(fakeClient);

    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(Collections.emptyList(), List.of(fileDto)));

    ArgumentCaptor<Map<String, List<ConnectionSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestConnection(suggestionCaptor.capture());

    var connectionSuggestion = suggestionCaptor.getValue();
    assertThat(connectionSuggestion).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID)).hasSize(1);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getRight()).isNotNull();
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getRight().getOrganization()).isEqualTo(ORGANIZATION);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).getConnectionSuggestion().getRight().getProjectKey()).isEqualTo(SLCORE_PROJECT_KEY);
    assertThat(connectionSuggestion.get(CONFIG_SCOPE_ID).get(0).isFromSharedConfiguration()).isTrue();
  }

}
