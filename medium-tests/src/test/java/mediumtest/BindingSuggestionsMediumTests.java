/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.testutils.GitUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetBindingSuggestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.test.utils.ProtobufUtils.protobufBody;

class BindingSuggestionsMediumTests {

  public static final String MYSONAR = "mysonar";
  public static final String CONFIG_SCOPE_ID = "myProject1";
  public static final String SLCORE_PROJECT_KEY = "org.sonarsource.sonarlint:sonarlint-core-parent";
  public static final String SLCORE_PROJECT_NAME = "SonarLint Core";
  public static final String PROJECT_ID = "my-project-id";
  public static final String REMOTE_URL = "git@github.com:myorg/myproject.git";

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @BeforeEach
  void init() {
    sonarqubeMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": " +
        "\"UP\"}")));
  }

  @SonarLintTest
  void test_connection_added_should_suggest_binding_with_no_matches(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "My Project 1")
      .start(fakeClient);
    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("No connections configured, skipping binding suggestions."));

    backend.getConnectionService()
      .didUpdateConnections(new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto(MYSONAR, sonarqubeMock.baseUrl(), true)), List.of()));

    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("Found 0 suggestions for configuration scope '" + CONFIG_SCOPE_ID + "'"));
    verify(fakeClient, never()).suggestBinding(any());
  }

  @SonarLintTest
  void test_connection_added_should_suggest_binding_with_matches(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "sonarlint-core")
      .start(fakeClient);
    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("No connections configured, skipping binding suggestions."));

    sonarqubeMock.stubFor(get("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1")
      .willReturn(aResponse().withResponseBody(protobufBody(Components.SearchWsResponse.newBuilder()
        .addComponents(Components.Component.newBuilder()
          .setKey(SLCORE_PROJECT_KEY)
          .setName(SLCORE_PROJECT_NAME)
          .build())
        .setPaging(Common.Paging.newBuilder().setTotal(1).build())
        .build()))));

    backend.getConnectionService()
      .didUpdateConnections(new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto(MYSONAR, sonarqubeMock.baseUrl(), true)), List.of()));

    ArgumentCaptor<Map<String, List<BindingSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestBinding(suggestionCaptor.capture());

    var bindingSuggestions = suggestionCaptor.getValue();
    assertThat(bindingSuggestions).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName, BindingSuggestionDto::isFromSharedConfiguration)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME, false));
  }

  @SonarLintTest
  void test_project_added_should_suggest_binding_with_matches(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection(MYSONAR, sonarqubeMock.baseUrl())
      .start(fakeClient);

    sonarqubeMock.stubFor(get("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1")
      .willReturn(aResponse().withResponseBody(protobufBody(Components.SearchWsResponse.newBuilder()
        .addComponents(Components.Component.newBuilder()
          .setKey(SLCORE_PROJECT_KEY)
          .setName(SLCORE_PROJECT_NAME)
          .build())
        .setPaging(Common.Paging.newBuilder().setTotal(1).build())
        .build()))));

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    ArgumentCaptor<Map<String, List<BindingSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestBinding(suggestionCaptor.capture());

    var bindingSuggestions = suggestionCaptor.getValue();
    assertThat(bindingSuggestions).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName, BindingSuggestionDto::isFromSharedConfiguration)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME, false));
  }

  @SonarLintTest
  void test_uses_binding_clues_when_initializing_fs(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    var clue = tmp.resolve("sonar-project.properties");
    Files.writeString(clue, "sonar.projectKey=" + SLCORE_PROJECT_KEY + "\nsonar.projectName=" + SLCORE_PROJECT_NAME, StandardCharsets.UTF_8);
    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(new ClientFileDto(clue.toUri(), Paths.get("sonar-project.properties"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null, null, true)))
      .build();

    var backend = harness.newBackend()
      .withSonarQubeConnection(MYSONAR, sonarqubeMock.baseUrl())
      .withSonarQubeConnection("another")
      .start(fakeClient);

    sonarqubeMock.stubFor(get("/api/components/show.protobuf?component=org.sonarsource.sonarlint%3Asonarlint-core-parent")
      .willReturn(aResponse().withResponseBody(protobufBody(Components.ShowWsResponse.newBuilder()
        .setComponent(Components.Component.newBuilder()
          .setKey(SLCORE_PROJECT_KEY)
          .setName(SLCORE_PROJECT_NAME)
          .build())
        .build()))));

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    ArgumentCaptor<Map<String, List<BindingSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestBinding(suggestionCaptor.capture());

    var bindingSuggestions = suggestionCaptor.getValue();
    assertThat(bindingSuggestions).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName, BindingSuggestionDto::isFromSharedConfiguration)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME, false));
  }

  @SonarLintTest
  void test_uses_binding_clues_when_updating_fs(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    var fakeClient = harness.newFakeClient()
      .build();

    var backend = harness.newBackend()
      .withSonarQubeConnection(MYSONAR, sonarqubeMock.baseUrl())
      .withSonarQubeConnection("another")
      .start(fakeClient);

    sonarqubeMock.stubFor(get("/api/components/show.protobuf?component=org.sonarsource.sonarlint%3Asonarlint-core-parent")
      .willReturn(aResponse().withResponseBody(protobufBody(Components.ShowWsResponse.newBuilder()
        .setComponent(Components.Component.newBuilder()
          .setKey(SLCORE_PROJECT_KEY)
          .setName(SLCORE_PROJECT_NAME)
          .build())
        .build()))));

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    // Without binding clue, there is no matching connection/project, so the list of suggestion is empty
    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("Found 0 suggestions for configuration scope '" + CONFIG_SCOPE_ID + "'"));
    verify(fakeClient, never()).suggestBinding(any());

    // Now add a binding clue to the FS
    var clue = tmp.resolve("sonar-project.properties");
    Files.writeString(clue, "sonar.projectKey=" + SLCORE_PROJECT_KEY + "\nsonar.projectName=" + SLCORE_PROJECT_NAME, StandardCharsets.UTF_8);

    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
      List.of(new ClientFileDto(clue.toUri(), Paths.get("sonar-project.properties"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name()
        , clue, null, null, true)),
      List.of(),
      List.of()
    ));

    ArgumentCaptor<Map<String, List<BindingSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestBinding(suggestionCaptor.capture());

    var bindingSuggestions = suggestionCaptor.getAllValues().get(0);
    assertThat(bindingSuggestions).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName, BindingSuggestionDto::isFromSharedConfiguration)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME, false));
  }

  @SonarLintTest
  void test_binding_suggestion_via_service(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection(MYSONAR, sonarqubeMock.baseUrl())
      .start(fakeClient);

    sonarqubeMock.stubFor(get("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1")
      .willReturn(aResponse().withResponseBody(protobufBody(Components.SearchWsResponse.newBuilder()
        .addComponents(Components.Component.newBuilder()
          .setKey(SLCORE_PROJECT_KEY)
          .setName(SLCORE_PROJECT_NAME)
          .build())
        .setPaging(Common.Paging.newBuilder().setTotal(1).build())
        .build()))));

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    // Ignore the automatic binding suggestions
    verify(fakeClient, timeout(5000)).suggestBinding(any());

    var bindingParamsCompletableFuture = backend.getBindingService().getBindingSuggestions(new GetBindingSuggestionParams(CONFIG_SCOPE_ID, MYSONAR));
    var bindingSuggestions = bindingParamsCompletableFuture.get().getSuggestions();
    assertThat(bindingSuggestions).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName, BindingSuggestionDto::isFromSharedConfiguration)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME, false));
  }

  @SonarLintTest
  void test_uses_binding_clues_from_shared_configuration_when_updating_fs(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    var fakeClient = harness.newFakeClient()
      .build();

    var backend = harness.newBackend()
      .withSonarQubeConnection(MYSONAR, sonarqubeMock.baseUrl())
      .withSonarQubeConnection("another")
      .start(fakeClient);

    sonarqubeMock.stubFor(get("/api/components/show.protobuf?component=org.sonarsource.sonarlint%3Asonarlint-core-parent")
      .willReturn(aResponse().withResponseBody(protobufBody(Components.ShowWsResponse.newBuilder()
        .setComponent(Components.Component.newBuilder()
          .setKey(SLCORE_PROJECT_KEY)
          .setName(SLCORE_PROJECT_NAME)
          .build())
        .build()))));

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    // Without binding clue, there is no matching connection/project, so the list of suggestion is empty
    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("Found 0 suggestions for configuration scope '" + CONFIG_SCOPE_ID + "'"));
    verify(fakeClient, never()).suggestBinding(any());

    // Now add a binding clue to the FS
    var sonarlintDir = tmp.resolve(".sonarlint/");
    Files.createDirectory(sonarlintDir);
    var clue = tmp.resolve(".sonarlint/connectedMode.json");
    Files.writeString(clue, "{\"projectKey\": \"" + SLCORE_PROJECT_KEY + "\",\"sonarQubeUri\": \"" + sonarqubeMock.baseUrl() + "\"}", StandardCharsets.UTF_8);

    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
      List.of(new ClientFileDto(clue.toUri(), Paths.get(".sonarlint/connectedMode.json"), CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(), clue, null, null, true)),
      List.of(),
      List.of())
    );

    ArgumentCaptor<Map<String, List<BindingSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestBinding(suggestionCaptor.capture());

    var bindingSuggestions = suggestionCaptor.getAllValues().get(0);
    assertThat(bindingSuggestions).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName, BindingSuggestionDto::isFromSharedConfiguration)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME, true));
  }

  @SonarLintTest
  void should_suggest_binding_by_remote_url_when_no_other_suggestions_found(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    var gitRepo = tmp.resolve("git-repo");
    Files.createDirectory(gitRepo);
    var encodedUrl = UrlUtils.urlEncode(REMOTE_URL);
    var expectedPath = "/dop-translation/project-bindings?url=" + encodedUrl;
    var expectedSearchProjectsPath = "/api/components/search_projects?projectIds=" + PROJECT_ID + "&organization=orgKey";

    try (var git = GitUtils.createRepository(gitRepo)) {
      git.remoteAdd()
        .setName("origin")
        .setUri(new URIish(REMOTE_URL))
        .call();
    } catch (Exception e) {
      throw new RuntimeException("Failed to setup Git repository", e);
    }

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, gitRepo, List.of())
      .build();

    var scServer = harness.newFakeSonarCloudServer()
      .withOrganization("orgKey", organization ->
        organization.withProject(SLCORE_PROJECT_KEY, project -> project.withBranch("main")))
      .start();

    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(scServer.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(scServer.baseUrl())
      .withSonarCloudConnection(MYSONAR, "orgKey")
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "unmatched-project-name")
      .withTelemetryEnabled()
      .start(fakeClient);

    scServer.getMockServer().stubFor(get(urlEqualTo(expectedPath))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"bindings\":[{\"projectId\":\"" + PROJECT_ID + "\"}]}")));

    scServer.getMockServer().stubFor(get(urlEqualTo(expectedSearchProjectsPath))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"components\":[{\"key\":\"" + SLCORE_PROJECT_KEY + "\",\"name\":\"" + SLCORE_PROJECT_NAME + "\"}]}\n")));

    scServer.getMockServer().stubFor(get(urlPathMatching("/api/components/search\\.protobuf"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/x-protobuf")
        .withResponseBody(protobufBody(Components.SearchWsResponse.newBuilder()
          .addComponents(Components.Component.newBuilder()
            .setKey("my-project-key")
            .setName("My Project")
            .build())
          .setPaging(Common.Paging.newBuilder().setTotal(1).build())
          .build()))));

    ArgumentCaptor<Map<String, List<BindingSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestBinding(suggestionCaptor.capture());

    var bindingSuggestions = suggestionCaptor.getValue();
    assertThat(backend.telemetryFileContent().getSuggestedRemoteBindingsCount()).isEqualTo(1);
    assertThat(bindingSuggestions).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME));
  }

  @SonarLintTest
  void should_suggest_binding_by_remote_url_when_no_other_suggestions_found_for_sonarqube_server(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    var gitRepo = tmp.resolve("git-repo");
    Files.createDirectory(gitRepo);
    var encodedUrl = UrlUtils.urlEncode(REMOTE_URL);
    var expectedPath = "/api/v2/dop-translation/project-bindings?repositoryUrl=" + encodedUrl;

    try (var git = GitUtils.createRepository(gitRepo)) {
      git.remoteAdd()
        .setName("origin")
        .setUri(new URIish(REMOTE_URL))
        .call();
    } catch (Exception e) {
      throw new RuntimeException("Failed to setup Git repository", e);
    }

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, gitRepo, List.of())
      .build();

    var sqServer = harness.newFakeSonarQubeServer()
      .withProject(SLCORE_PROJECT_KEY, project -> project.withBranch("main"))
      .start();

    var backend = harness.newBackend()
      .withSonarQubeConnection(MYSONAR, sqServer.baseUrl())
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "unmatched-project-name")
      .withTelemetryEnabled()
      .start(fakeClient);

    sqServer.getMockServer().stubFor(get(urlEqualTo(expectedPath))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"projectBindings\":[{\"projectId\":\"" + PROJECT_ID + "\",\"projectKey\":\"" + SLCORE_PROJECT_KEY + "\"}]}")));

    var expectedProjectPath = "/api/components/show.protobuf?component=" + UrlUtils.urlEncode(SLCORE_PROJECT_KEY);
    sqServer.getMockServer().stubFor(get(urlEqualTo(expectedProjectPath))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/x-protobuf")
        .withResponseBody(protobufBody(Components.ShowWsResponse.newBuilder()
          .setComponent(Components.Component.newBuilder()
            .setKey(SLCORE_PROJECT_KEY)
            .setName(SLCORE_PROJECT_NAME)
            .setIsAiCodeFixEnabled(false)
            .build())
          .build()))));

    ArgumentCaptor<Map<String, List<BindingSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestBinding(suggestionCaptor.capture());

    var bindingSuggestions = suggestionCaptor.getValue();
    assertThat(backend.telemetryFileContent().getSuggestedRemoteBindingsCount()).isEqualTo(1);
    assertThat(bindingSuggestions).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME));
  }

  @SonarLintTest
  void should_return_empty_when_sqc_project_bindings_is_null(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException, GitAPIException, URISyntaxException {
    var gitRepo = tmp.resolve("git-repo");
    Files.createDirectory(gitRepo);
    var encodedUrl = UrlUtils.urlEncode(REMOTE_URL);
    var expectedPath = "/dop-translation/project-bindings?url=" + encodedUrl;

    try (var git = GitUtils.createRepository(gitRepo)) {
      git.remoteAdd()
        .setName("origin")
        .setUri(new URIish(REMOTE_URL))
        .call();
    }

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, gitRepo, List.of())
      .build();

    var scServer = harness.newFakeSonarCloudServer()
      .withOrganization("orgKey", organization ->
        organization.withProject(SLCORE_PROJECT_KEY, project -> project.withBranch("main")))
      .start();

    harness.newBackend()
      .withSonarQubeCloudEuRegionUri(scServer.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(scServer.baseUrl())
      .withSonarCloudConnection(MYSONAR, "orgKey")
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "unmatched-project-name")
      .start(fakeClient);

    scServer.getMockServer().stubFor(get(urlEqualTo(expectedPath))
      .willReturn(aResponse()
        .withStatus(500)
        .withBody("Internal Server Error")));

    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("Found 0 suggestions for configuration scope '" + CONFIG_SCOPE_ID + "'"));
    verify(fakeClient, never()).suggestBinding(any());
  }

  @SonarLintTest
  void should_return_empty_when_sqs_project_bindings_is_null(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException, GitAPIException, URISyntaxException {
    var gitRepo = tmp.resolve("git-repo");
    Files.createDirectory(gitRepo);
    var encodedUrl = UrlUtils.urlEncode(REMOTE_URL);
    var expectedPath = "/api/v2/dop-translation/project-bindings?repositoryUrl=" + encodedUrl;

    try (var git = GitUtils.createRepository(gitRepo)) {
      git.remoteAdd()
        .setName("origin")
        .setUri(new URIish(REMOTE_URL))
        .call();
    }

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, gitRepo, List.of())
      .build();

    var sqServer = harness.newFakeSonarQubeServer()
      .withProject(SLCORE_PROJECT_KEY, project -> project.withBranch("main"))
      .start();

    harness.newBackend()
      .withSonarQubeConnection(MYSONAR, sqServer.baseUrl())
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "unmatched-project-name")
      .start(fakeClient);

    sqServer.getMockServer().stubFor(get(urlEqualTo(expectedPath))
      .willReturn(aResponse()
        .withStatus(500)
        .withBody("Internal Server Error")));

    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("Found 0 suggestions for configuration scope '" + CONFIG_SCOPE_ID + "'"));
    verify(fakeClient, never()).suggestBinding(any());
  }

  @SonarLintTest
  void should_return_empty_when_sqc_search_response_is_null(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException, GitAPIException, URISyntaxException {
    var gitRepo = tmp.resolve("git-repo");
    Files.createDirectory(gitRepo);
    var encodedUrl = UrlUtils.urlEncode(REMOTE_URL);
    var expectedPath = "/dop-translation/project-bindings?url=" + encodedUrl;
    var expectedSearchProjectsPath = "/api/components/search_projects?projectIds=" + PROJECT_ID + "&organization=orgKey";

    try (var git = GitUtils.createRepository(gitRepo)) {
      git.remoteAdd()
        .setName("origin")
        .setUri(new URIish(REMOTE_URL))
        .call();
    }

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, gitRepo, List.of())
      .build();

    var scServer = harness.newFakeSonarCloudServer()
      .withOrganization("orgKey", organization ->
        organization.withProject(SLCORE_PROJECT_KEY, project -> project.withBranch("main")))
      .start();

    harness.newBackend()
      .withSonarQubeCloudEuRegionUri(scServer.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(scServer.baseUrl())
      .withSonarCloudConnection(MYSONAR, "orgKey")
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "unmatched-project-name")
      .start(fakeClient);

    scServer.getMockServer().stubFor(get(urlEqualTo(expectedPath))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"bindings\":[{\"projectId\":\"" + PROJECT_ID + "\"}]}")));

    scServer.getMockServer().stubFor(get(urlEqualTo(expectedSearchProjectsPath))
      .willReturn(aResponse()
        .withStatus(500)
        .withBody("Internal Server Error")));

    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("Found 0 suggestions for configuration scope '" + CONFIG_SCOPE_ID + "'"));
    verify(fakeClient, never()).suggestBinding(any());
  }

  @SonarLintTest
  void should_return_empty_when_sqs_server_project_is_not_present(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException, GitAPIException, URISyntaxException {
    var gitRepo = tmp.resolve("git-repo");
    Files.createDirectory(gitRepo);
    var encodedUrl = UrlUtils.urlEncode(REMOTE_URL);
    var expectedPath = "/api/v2/dop-translation/project-bindings?repositoryUrl=" + encodedUrl;
    var expectedProjectPath = "/api/components/show.protobuf?component=" + UrlUtils.urlEncode(SLCORE_PROJECT_KEY);

    try (var git = GitUtils.createRepository(gitRepo)) {
      git.remoteAdd()
        .setName("origin")
        .setUri(new URIish(REMOTE_URL))
        .call();
    }

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, gitRepo, List.of())
      .build();

    var sqServer = harness.newFakeSonarQubeServer()
      .withProject(SLCORE_PROJECT_KEY, project -> project.withBranch("main"))
      .start();

    harness.newBackend()
      .withSonarQubeConnection(MYSONAR, sqServer.baseUrl())
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "unmatched-project-name")
      .start(fakeClient);

    sqServer.getMockServer().stubFor(get(urlEqualTo(expectedPath))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"projectBindings\":[{\"projectId\":\"" + PROJECT_ID + "\",\"projectKey\":\"" + SLCORE_PROJECT_KEY + "\"}]}")));

    sqServer.getMockServer().stubFor(get(urlEqualTo(expectedProjectPath))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody("Project not found")));

    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("Found 0 suggestions for configuration scope '" + CONFIG_SCOPE_ID + "'"));
    verify(fakeClient, never()).suggestBinding(any());
  }
}
