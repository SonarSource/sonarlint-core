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
package mediumtest.server.events;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRange;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityChangedOrClosedEvent;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS;

class ServerSentEventsMediumTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);
  private SonarLintTestRpcServer backend;
  private String oldSonarCloudUrl;

  @RegisterExtension
  static WireMockExtension sonarServerMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @BeforeEach
  void prepare() {
    oldSonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
    if (oldSonarCloudUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.url", oldSonarCloudUrl);
    }
  }

  @Nested
  class WhenScopeBound {
    @Test
    void should_subscribe_for_events_if_connected_to_sonarqube() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withUnboundConfigScope("configScope")
        .build();

      bind("configScope", "connectionId", "projectKey");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsExactly("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @Test
    void should_not_subscribe_for_events_if_sonarcloud_connection() {
      System.setProperty("sonarlint.internal.sonarcloud.url", sonarServerMock.baseUrl());
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarCloudConnection("connectionId")
        .withUnboundConfigScope("configScope")
        .build();

      bind("configScope", "connectionId", "projectKey");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    private void bind(String configScopeId, String connectionId, String projectKey) {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(connectionId, projectKey, true)));
    }

    @Test
    void should_not_resubscribe_for_events_if_sonarqube_connection_and_binding_is_the_same() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      bind("configScope", "connectionId", "projectKey");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().size() == 1);
    }
  }

  @Nested
  class WhenUnbindingScope {
    @Test
    void should_not_resubscribe_for_events_if_sonarqube_connection() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      unbind("configScope");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().size() == 1);
    }

    @Test
    void should_unsubscribe_for_events_if_sonarqube_connection_and_other_projects_bound() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope1", "connectionId", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey2")
        .build();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      unbind("configScope1");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey2,projectKey1&languages=java,js",
          "/api/push/sonarlint_events?projectKeys=projectKey2&languages=java,js"));
    }

    private void unbind(String configScope) {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScope, new BindingConfigurationDto(null, null, true)));
    }
  }

  @Nested
  class WhenScopeAdded {
    @Test
    void should_subscribe_if_bound_to_sonarqube() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .build();

      addConfigurationScope("configScope", "connectionId", "projectKey");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsExactly("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @Test
    void should_not_subscribe_if_not_bound() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .build();

      addConfigurationScope("configScope", null, null);

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @Test
    void should_not_subscribe_if_bound_to_sonarcloud() {
      System.setProperty("sonarlint.internal.sonarcloud.url", sonarServerMock.baseUrl());
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarCloudConnection("connectionId")
        .build();

      addConfigurationScope("configScope", "connectionId", "projectKey");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    private void addConfigurationScope(String configScope, String connectionId, String projectKey) {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(configScope, null, true, "name", new BindingConfigurationDto(connectionId, projectKey, true)))));
    }
  }

  @Nested
  class WhenScopeRemoved {

    @Test
    void should_do_nothing_if_scope_was_not_bound() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withUnboundConfigScope("configScope")
        .build();

      removeScope("configScope");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @Test
    void should_do_nothing_if_scope_was_bound_to_sonarcloud() {
      System.setProperty("sonarlint.internal.sonarcloud.url", sonarServerMock.baseUrl());
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarCloudConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      removeScope("configScope");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @Test
    void should_close_connection_when_if_scope_was_bound_to_sonarcloud_and_no_other_project_interested() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      removeScope("configScope");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().size() == 1);
    }

    @Test
    void should_keep_connection_if_scope_was_bound_to_sonarqube_and_another_scope_is_interested_in_the_same_project() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope1", "connectionId", "projectKey")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      removeScope("configScope1");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @Test
    void should_reopen_connection_if_scope_was_bound_to_sonarqube_and_another_scope_is_interested_in_a_different_project() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope1", "connectionId", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey2")
        .build();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      removeScope("configScope1");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey2,projectKey1&languages=java,js",
          "/api/push/sonarlint_events?projectKeys=projectKey2&languages=java,js"));
    }

    private void removeScope(String configScope) {
      backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(configScope));
    }
  }

  @Nested
  class WhenConnectionCredentialsChanged {

    @Test
    void should_resubscribe_if_sonarqube_connection_was_open() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      notifyCredentialsChanged("connectionId");

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js",
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @Test
    void should_do_nothing_if_sonarcloud() {
      System.setProperty("sonarlint.internal.sonarcloud.url", sonarServerMock.baseUrl());
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarCloudConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      notifyCredentialsChanged("connectionId");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @Test
    void should_do_nothing_if_sonarqube_connection_was_not_open() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .build();

      notifyCredentialsChanged("connectionId");

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    private void notifyCredentialsChanged(String connectionId) {
      backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams(connectionId));
    }
  }

  @Nested
  class WhenConnectionAdded {

    @Test
    void should_do_nothing_if_no_scope_is_bound() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withUnboundConfigScope("configScope")
        .build();

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionId", "url", true)), Collections.emptyList()));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @Test
    void should_do_nothing_if_sonarcloud() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(Collections.emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", true))));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @Test
    void should_open_connection_when_bound_scope_exists() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      backend.getConnectionService().didUpdateConnections(
        new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionId", sonarServerMock.baseUrl(), true)), Collections.emptyList()));

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }
  }

  @Nested
  class WhenConnectionRemoved {

    @Test
    void should_do_nothing_if_sonarcloud() {
      System.setProperty("sonarlint.internal.sonarcloud.url", sonarServerMock.baseUrl());
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarCloudConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(Collections.emptyList(), Collections.emptyList()));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }

    @Test
    void should_close_active_connection_if_sonarqube() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(Collections.emptyList(), Collections.emptyList()));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().size() == 1);
    }
  }

  @Nested
  class WhenConnectionUpdated {

    @Test
    void should_resubscribe_if_sonarqube_connection_active() {
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", sonarServerMock.baseUrl())
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(requestedPaths()).hasSize(1));

      backend.getConnectionService().didUpdateConnections(
        new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionId", sonarServerMock.baseUrl(), false)), Collections.emptyList()));

      await().atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(requestedPaths()).containsOnly(
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js",
          "/api/push/sonarlint_events?projectKeys=projectKey&languages=java,js"));
    }

    @Test
    void should_not_resubscribe_if_sonarcloud() {
      System.setProperty("sonarlint.internal.sonarcloud.url", sonarServerMock.baseUrl());
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarCloudConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(), Collections.emptyList()));

      await().during(Duration.ofMillis(300)).until(() -> requestedPaths().isEmpty());
    }
  }

  @Nested
  class WhenReceivingIssueChangedEvent {

    private ServerFixture.Server serverWithTaintIssues;

    @AfterEach
    void shutdown() {
      if (serverWithTaintIssues != null) {
        serverWithTaintIssues.shutdown();
      }
    }

    @Test
    void should_forward_taint_events_to_client() {
      var fakeClient = spy(newFakeClient().build());

      var projectKey = "projectKey";
      var branchName = "branchName";
      serverWithTaintIssues = newSonarQubeServer("10.0")
        .withProject(projectKey,
          project -> project.withBranch(branchName,
            branch -> branch.withTaintIssue("key1", "ruleKey", "msg", "author", "file/path", "REVIEWED", "SAFE", Instant.now(), new TextRange(1, 0, 3, 4), RuleType.BUG)
              .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))))
        .start();

      serverWithTaintIssues.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java,js")
        .inScenario("Single event")
        .whenScenarioStateIs(STARTED)
        .willReturn(okForContentType("text/event-stream", "event: IssueChanged\n" +
          "data: {" +
          "\"projectKey\": \"" + projectKey + "\"," +
          "\"issues\": [{" +
          "  \"issueKey\": \"key1\"," +
          "  \"branchName\": \"" + branchName + "\"" +
          "}]," +
          "\"userType\": \"BUG\"" +
          "}\n\n")
            // Add a delay to ensure the auto-sync of the issue storage had been completed
            .withFixedDelay(2000))
        .willSetStateTo("Event delivered"));
      // avoid later reconnection
      serverWithTaintIssues.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java,js")
        .inScenario("Single event")
        .whenScenarioStateIs("Event delivered")
        .willReturn(notFound()));

      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withProjectSynchronization()
        .withSonarQubeConnection("connectionId", serverWithTaintIssues)
        .withBoundConfigScope("configScope", "connectionId", projectKey, branchName)
        .build(fakeClient);

      var captor = ArgumentCaptor.forClass(DidReceiveServerTaintVulnerabilityChangedOrClosedEvent.class);
      verify(fakeClient, timeout(3000)).didReceiveServerTaintVulnerabilityChangedOrClosedEvent(captor.capture());

      assertThat(captor.getValue().getConnectionId()).isEqualTo("connectionId");
      assertThat(captor.getValue().getSonarProjectKey()).isEqualTo(projectKey);
    }
  }

  private List<String> requestedPaths() {
    var pattern = Pattern.compile("/api/push/sonarlint_events*");
    return sonarServerMock.getAllServeEvents()
      .stream()
      .map(ServeEvent::getRequest)
      .map(LoggedRequest::getUrl)
      .filter(pattern.asPredicate())
      .collect(Collectors.toList());
  }

}
