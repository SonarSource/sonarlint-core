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
package mediumtest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.clientapi.config.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidAddConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingSuggestionsMediumTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  public static final String MYSONAR = "mysonar";
  public static final String CONFIG_SCOPE_ID = "myProject1";
  public static final String SLCORE_PROJECT_KEY = "org.sonarsource.sonarlint:sonarlint-core-parent";
  public static final String SLCORE_PROJECT_NAME = "SonarLint Core";
  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  private SonarLintClient fakeClient;
  private SonarLintBackend backend;

  @BeforeEach
  void setup() {
    fakeClient = mock(SonarLintClient.class);
    backend = new SonarLintBackendImpl(fakeClient);
    SonarLintLogger.setTarget((formattedMessage, level) -> System.out.println(level + " " + formattedMessage));
    when(fakeClient.getHttpClient(MYSONAR)).thenReturn(MockWebServerExtensionWithProtobuf.httpClient());
    when(fakeClient.findFileByNamesInScope(any())).thenReturn(CompletableFuture.completedFuture(new FindFileByNamesInScopeResponse(List.of())));
  }

  @AfterEach
  void stop() throws ExecutionException, InterruptedException, TimeoutException {
    backend.shutdown().get(5, TimeUnit.SECONDS);
  }

  @Test
  void test_connection_added_should_suggest_binding_with_no_matches() {
    backend.getConnectionService().initialize(new InitializeParams(List.of(), List.of()));
    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "My Project 1",
            new BindingConfigurationDto(null, null, false)))));
    await().until(() -> logTester.logs(), logs -> logs.contains("No connections configured, skipping binding suggestions."));

    backend.getConnectionService()
      .didAddConnection(new DidAddConnectionParams(new SonarQubeConnectionConfigurationDto(MYSONAR, mockWebServerExtension.endpointParams().getBaseUrl())));

    var params = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(fakeClient, timeout(5000).times(1)).suggestBinding(params.capture());

    assertThat(params.getValue().getSuggestions()).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(params.getValue().getSuggestions().get(CONFIG_SCOPE_ID)).isEmpty();
  }

  @Test
  void test_connection_added_should_suggest_binding_with_matches() {
    backend.getConnectionService().initialize(new InitializeParams(List.of(), List.of()));
    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));
    await().until(() -> logTester.logs(), logs -> logs.contains("No connections configured, skipping binding suggestions."));

    mockWebServerExtension.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1", Components.SearchWsResponse.newBuilder()
      .addComponents(Components.Component.newBuilder()
        .setKey(SLCORE_PROJECT_KEY)
        .setName(SLCORE_PROJECT_NAME)
        .build())
      .setPaging(Common.Paging.newBuilder().setTotal(1).build())
      .build());

    backend.getConnectionService()
      .didAddConnection(new DidAddConnectionParams(new SonarQubeConnectionConfigurationDto(MYSONAR, mockWebServerExtension.endpointParams().getBaseUrl())));

    var params = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(fakeClient, timeout(5000).times(1)).suggestBinding(params.capture());

    assertThat(params.getValue().getSuggestions()).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(params.getValue().getSuggestions().get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME));
  }

  @Test
  void test_project_added_should_suggest_binding_with_matches() {
    backend.getConnectionService().initialize(
      new InitializeParams(List.of(new SonarQubeConnectionConfigurationDto(MYSONAR, mockWebServerExtension.endpointParams().getBaseUrl())), List.of()));

    mockWebServerExtension.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1", Components.SearchWsResponse.newBuilder()
      .addComponents(Components.Component.newBuilder()
        .setKey(SLCORE_PROJECT_KEY)
        .setName(SLCORE_PROJECT_NAME)
        .build())
      .setPaging(Common.Paging.newBuilder().setTotal(1).build())
      .build());

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    var params = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(fakeClient, timeout(5000).times(1)).suggestBinding(params.capture());

    assertThat(params.getValue().getSuggestions()).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(params.getValue().getSuggestions().get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME));
  }

  @Test
  void test_uses_binding_clues() {
    backend.getConnectionService().initialize(
      new InitializeParams(List.of(
        new SonarQubeConnectionConfigurationDto(MYSONAR, mockWebServerExtension.endpointParams().getBaseUrl()),
        new SonarQubeConnectionConfigurationDto("another", "http://foo")),
        List.of()));

    mockWebServerExtension.addProtobufResponse("/api/components/show.protobuf?component=org.sonarsource.sonarlint%3Asonarlint-core-parent", Components.ShowWsResponse.newBuilder()
      .setComponent(Components.Component.newBuilder()
        .setKey(SLCORE_PROJECT_KEY)
        .setName(SLCORE_PROJECT_NAME)
        .build())
      .build());

    when(fakeClient.findFileByNamesInScope(any()))
      .thenReturn(CompletableFuture.completedFuture(
        new FindFileByNamesInScopeResponse(List.of(
          new FoundFileDto("sonar-project.properties", "/home/user/Project/sonar-project.properties",
            "sonar.host.url=" + mockWebServerExtension.endpointParams().getBaseUrl() + "\nsonar.projectKey=" + SLCORE_PROJECT_KEY)))));

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "sonarlint-core",
            new BindingConfigurationDto(null, null, false)))));

    var params = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(fakeClient, timeout(5000).times(1)).suggestBinding(params.capture());

    assertThat(params.getValue().getSuggestions()).containsOnlyKeys(CONFIG_SCOPE_ID);
    assertThat(params.getValue().getSuggestions().get(CONFIG_SCOPE_ID))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsExactly(tuple(MYSONAR, SLCORE_PROJECT_KEY, SLCORE_PROJECT_NAME));
  }

}
