/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package mediumtest.server.events;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SERVER_SENT_EVENTS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS;

class ServerSentEventsMediumTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  @RegisterExtension
  static WireMockExtension sonarServerMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @BeforeEach
  void init() {
    sonarServerMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": " +
        "\"UP\"}")));
  }

  @Nested
  class WhenScopeBound {
    @SonarLintTest
    void should_subscribe_for_events_if_connected_to_sonarqube(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withUnboundConfigScope("configScope")
        .start();

      bind(backend, "configScope", "connectionId", "projectKey");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsExactly("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @SonarLintTest
    void should_not_subscribe_for_events_if_sonarcloud_connection(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withSonarQubeCloudEuRegionUri(sonarServerMock.baseUrl())
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarCloudConnection("connectionId")
        .withUnboundConfigScope("configScope")
        .start();

      bind(backend, "configScope", "connectionId", "projectKey");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @SonarLintTest
    void should_not_resubscribe_for_events_if_sonarqube_connection_and_binding_is_the_same(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      bind(backend, "configScope", "connectionId", "projectKey");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().size() == 1);
    }

    private void bind(SonarLintTestRpcServer backend, String configScopeId, String connectionId, String projectKey) {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(connectionId, projectKey, true)));
    }
  }

  @Nested
  class WhenUnbindingScope {
    @SonarLintTest
    void should_not_resubscribe_for_events_if_sonarqube_connection(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      unbind(backend, "configScope");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().size() == 1);
    }

    @SonarLintTest
    void should_unsubscribe_for_events_if_sonarqube_connection_and_other_projects_bound(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope1", "connectionId", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey2")
        .start();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      unbind(backend, "configScope1");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey2,projectKey1&languages=java,js",
          "/api/push/sonarlint_events?projectKeys=projectKey2&languages=java,js"));
    }

    private void unbind(SonarLintTestRpcServer backend, String configScope) {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScope, new BindingConfigurationDto(null, null, true)));
    }
  }

  @Nested
  class WhenScopeAdded {
    @SonarLintTest
    void should_subscribe_if_bound_to_sonarqube(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .start();

      addConfigurationScope(backend, "configScope", "connectionId", "projectKey");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsExactly("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @SonarLintTest
    void should_log_subscription_errors(SonarLintTestHarness harness) {
      var client = harness.newFakeClient().build();
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .start(client);
      var projectKey = "projectKey";

      sonarServerMock.stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java,js")
        .willReturn(jsonResponse("{\"errors\":[{\"msg\":\"Some error from server\"}]}", 400)));

      addConfigurationScope(backend, "configScope", "connectionId", projectKey);

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsExactly("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));

      await().atMost(Duration.ofSeconds(1))
        .untilAsserted(() -> assertThat(client.getLogMessages())
          .contains(
            "Cannot connect to server event-stream (400), retrying in 60s",
            "Received event-stream data while not connected: {\"errors\":[{\"msg\":\"Some error from server\"}]}"));
    }

    @SonarLintTest
    void should_not_subscribe_if_not_bound(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .start();

      addConfigurationScope(backend, "configScope", null, null);

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @SonarLintTest
    void should_not_subscribe_if_bound_to_sonarcloud(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withSonarQubeCloudEuRegionUri(sonarServerMock.baseUrl())
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarCloudConnection("connectionId")
        .start();

      addConfigurationScope(backend, "configScope", "connectionId", "projectKey");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    private void addConfigurationScope(SonarLintTestRpcServer backend, String configScope, String connectionId, String projectKey) {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(configScope, null, true, "name", new BindingConfigurationDto(connectionId, projectKey, true)))));
    }
  }

  @Nested
  class WhenScopeRemoved {

    @SonarLintTest
    void should_do_nothing_if_scope_was_not_bound(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withUnboundConfigScope("configScope")
        .start();

      removeScope(backend, "configScope");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @SonarLintTest
    void should_do_nothing_if_scope_was_bound_to_sonarcloud(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withSonarQubeCloudEuRegionUri(sonarServerMock.baseUrl())
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarCloudConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();

      removeScope(backend, "configScope");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @SonarLintTest
    void should_close_connection_when_if_scope_was_bound_to_sonarcloud_and_no_other_project_interested(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      removeScope(backend, "configScope");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().size() == 1);
    }

    @SonarLintTest
    void should_keep_connection_if_scope_was_bound_to_sonarqube_and_another_scope_is_interested_in_the_same_project(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope1", "connectionId", "projectKey")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey")
        .start();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      removeScope(backend, "configScope1");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @SonarLintTest
    void should_reopen_connection_if_scope_was_bound_to_sonarqube_and_another_scope_is_interested_in_a_different_project(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope1", "connectionId", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey2")
        .start();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      removeScope(backend, "configScope1");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey2,projectKey1&languages=java,js",
          "/api/push/sonarlint_events?projectKeys=projectKey2&languages=java,js"));
    }

    private void removeScope(SonarLintTestRpcServer backend, String configScope) {
      backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(configScope));
    }
  }

  @Nested
  class WhenConnectionCredentialsChanged {

    @SonarLintTest
    void should_resubscribe_if_sonarqube_connection_was_open(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      notifyCredentialsChanged(backend, "connectionId");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js",
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @SonarLintTest
    void should_do_nothing_if_sonarcloud(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withSonarQubeCloudEuRegionUri(sonarServerMock.baseUrl())
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarCloudConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();

      notifyCredentialsChanged(backend, "connectionId");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @SonarLintTest
    void should_do_nothing_if_sonarqube_connection_was_not_open(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .start();

      notifyCredentialsChanged(backend, "connectionId");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    private void notifyCredentialsChanged(SonarLintTestRpcServer backend, String connectionId) {
      backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams(connectionId));
    }
  }

  @Nested
  class WhenConnectionAdded {

    @SonarLintTest
    void should_do_nothing_if_no_scope_is_bound(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withUnboundConfigScope("configScope")
        .start();

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionId", "url", true)), Collections.emptyList()));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @SonarLintTest
    void should_do_nothing_if_sonarcloud(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();

      backend.getConnectionService()
        .didUpdateConnections(
          new DidUpdateConnectionsParams(Collections.emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", SonarCloudRegion.EU, true))));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @SonarLintTest
    void should_open_connection_when_bound_scope_exists(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();

      backend.getConnectionService().didUpdateConnections(
        new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionId", sonarServerMock.baseUrl(), true)), Collections.emptyList()));

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }
  }

  @Nested
  class WhenConnectionRemoved {

    @SonarLintTest
    void should_do_nothing_if_sonarcloud(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withSonarQubeCloudEuRegionUri(sonarServerMock.baseUrl())
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarCloudConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(Collections.emptyList(), Collections.emptyList()));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @SonarLintTest
    void should_close_active_connection_if_sonarqube(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(Collections.emptyList(), Collections.emptyList()));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().size() == 1);
    }
  }

  @Nested
  class WhenConnectionUpdated {

    @SonarLintTest
    void should_resubscribe_if_sonarqube_connection_active(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      backend.getConnectionService().didUpdateConnections(
        new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionId", sonarServerMock.baseUrl(), false)), Collections.emptyList()));

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js",
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @SonarLintTest
    void should_not_resubscribe_if_sonarcloud(SonarLintTestHarness harness) {
      var backend = harness.newBackend()
        .withSonarQubeCloudEuRegionUri(sonarServerMock.baseUrl())
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS)
        .withSonarCloudConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start();

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(), Collections.emptyList()));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }
  }

  @Nested
  class WhenReceivingIssueChangedEvent {

    @SonarLintTest
    void should_forward_taint_events_to_client(SonarLintTestHarness harness) {
      var fakeClient = harness.newFakeClient().build();
      var branchName = "branchName";
      when(fakeClient.matchSonarProjectBranch(eq("configScope"), eq("main"), eq(Set.of("main", branchName)), any())).thenReturn(branchName);
      var projectKey = "projectKey";
      var introductionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS);
      var serverWithTaintIssues = harness.newFakeSonarQubeServer("10.0")
        .withServerSentEventsEnabled()
        .withProject(projectKey,
          project -> project.withBranch(branchName,
            branch -> branch
              .withTaintIssue("key1", "ruleKey", "msg", "author", "file/path", "REVIEWED", "WONTFIX", introductionDate, new TextRange(1, 0, 3, 4), RuleType.VULNERABILITY)
              .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))))
        .start();
      harness.newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withBackendCapability(SERVER_SENT_EVENTS, FULL_SYNCHRONIZATION)
        .withSonarQubeConnection("connectionId", serverWithTaintIssues)
        .withBoundConfigScope("configScope", "connectionId", projectKey)
        .start(fakeClient);
      fakeClient.waitForSynchronization();
      ArgumentCaptor<List<TaintVulnerabilityDto>> captor = ArgumentCaptor.forClass(List.class);
      verify(fakeClient, timeout(3000)).didChangeTaintVulnerabilities(eq("configScope"), eq(Set.of()), captor.capture(), eq(List.of()));

      serverWithTaintIssues.pushEvent("event: IssueChanged\n" +
        "data: {" +
        "\"projectKey\": \"" + projectKey + "\"," +
        "\"issues\": [{" +
        "  \"issueKey\": \"key1\"," +
        "  \"branchName\": \"" + branchName + "\"" +
        "}]," +
        "\"userType\": \"BUG\"" +
        "}\n\n");

      // initial sync
      assertThat(captor.getValue())
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(List.of(new TaintVulnerabilityDto(UUID.randomUUID(), "key1", true, null, "ruleKey", "msg", Paths.get("file/path"), introductionDate,
          Either.forLeft(new StandardModeDetails(IssueSeverity.MAJOR, org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.BUG)), Collections.emptyList(),
          new TextRangeWithHashDto(1, 0, 3, 4, "hash"), null, true, false)));

      reset(fakeClient);
      waitAtMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getTaintVulnerabilityChanges()).isNotEmpty());

      // server event
      assertThat(captor.getValue())
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(List.of(new TaintVulnerabilityDto(UUID.randomUUID(), "key1", true, null, "ruleKey", "msg", Paths.get("file/path"), introductionDate,
          Either.forLeft(new StandardModeDetails(IssueSeverity.MAJOR, org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.BUG)), Collections.emptyList(),
          new TextRangeWithHashDto(1, 0, 3, 4, "hash"), null, true, false)));
    }
  }

  private List<String> requestedPaths() {
    var pattern = Pattern.compile("/api/push/sonarlint_events*");
    return sonarServerMock.getAllServeEvents()
      .stream()
      .map(ServeEvent::getRequest)
      .map(LoggedRequest::getUrl)
      .filter(pattern.asPredicate())
      .toList();
  }

}
