/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetBindingSuggestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static testutils.TestUtils.protobufBody;

class BindingSuggestionsMediumTests {

  public static final String MYSONAR = "mysonar";
  public static final String CONFIG_SCOPE_ID = "myProject1";
  public static final String SLCORE_PROJECT_KEY = "org.sonarsource.sonarlint:sonarlint-core-parent";
  public static final String SLCORE_PROJECT_NAME = "SonarLint Core";

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
  void test_connection_added_should_suggest_binding_with_no_matches() {
    var fakeClient = spy(newFakeClient().build());
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "My Project 1")
      .build(fakeClient);
    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("No connections configured, skipping binding suggestions."));

    backend.getConnectionService()
      .didUpdateConnections(new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto(MYSONAR, sonarqubeMock.baseUrl(), true)), List.of()));

    ArgumentCaptor<Map<String, List<BindingSuggestionDto>>> suggestionCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fakeClient, timeout(5000)).suggestBinding(suggestionCaptor.capture());

    var bindingSuggestions = suggestionCaptor.getValue();
    assertThat(bindingSuggestions).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID)).isEmpty();
  }

  @Test
  void test_connection_added_should_suggest_binding_with_matches() {
    var fakeClient = spy(newFakeClient().build());
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "sonarlint-core")
      .build(fakeClient);
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
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME));
  }

  @Test
  void test_project_added_should_suggest_binding_with_matches() {
    var fakeClient = spy(newFakeClient().build());
    backend = newBackend()
      .withSonarQubeConnection(MYSONAR, sonarqubeMock.baseUrl())
      .build(fakeClient);

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
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME));
  }

  @Test
  void test_uses_binding_clues() {
    var fakeClient = spy(newFakeClient()
      .withFoundFile("sonar-project.properties", "/home/user/Project/sonar-project.properties",
        "sonar.host.url=" + sonarqubeMock.baseUrl() + "\nsonar.projectKey=" + SLCORE_PROJECT_KEY)
      .build());
    backend = newBackend()
      .withSonarQubeConnection(MYSONAR, sonarqubeMock.baseUrl())
      .withSonarQubeConnection("another")
      .build(fakeClient);

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
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME));
  }

  @Test
  void test_binding_suggestion_via_service() throws ExecutionException, InterruptedException {
    var fakeClient = spy(newFakeClient().build());
    backend = newBackend()
      .withSonarQubeConnection(MYSONAR, sonarqubeMock.baseUrl())
      .build(fakeClient);

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
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME));
  }
}
