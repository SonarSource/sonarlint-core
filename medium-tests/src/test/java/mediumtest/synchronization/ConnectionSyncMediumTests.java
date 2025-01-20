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
package mediumtest.synchronization;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.newBackend;
import static org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.newFakeClient;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.newSonarQubeServer;

class ConnectionSyncMediumTests {
  public static final String CONNECTION_ID = "connectionId";
  public static final String SCOPE_ID = "scopeId";

  @SonarLintTest
  void it_should_cache_extracted_rule_metadata_per_connection(SonarLintTestHarness harness) {
    var client = harness.newFakeClient()
      .withCredentials(CONNECTION_ID, "user", "pw")
      .build();
    when(client.getClientLiveDescription()).thenReturn(this.getClass().getName());

    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage.withPlugin(TestPlugin.JAVA))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .build(client);
    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Binding suggestion computation queued for config scopes 'scopeId'..."));

    assertThat(client.getLogMessages()).doesNotContain("Extracting rules metadata for connection 'connectionId'");

    // Trigger lazy initialization of the rules metadata
    getEffectiveRuleDetails(backend, SCOPE_ID, "java:S106");
    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Extracting rules metadata for connection 'connectionId'"));

    // Second call should not trigger init as results are already cached
    client.clearLogs();

    getEffectiveRuleDetails(backend, SCOPE_ID, "java:S106");
  }

  @SonarLintTest
  void it_should_evict_cache_when_connection_is_removed(SonarLintTestHarness harness) {
    var client = harness.newFakeClient()
      .withCredentials(CONNECTION_ID, "user", "pw")
      .build();
    when(client.getClientLiveDescription()).thenReturn(this.getClass().getName());

    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage.withPlugin(TestPlugin.JAVA))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .build(client);
    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Binding suggestion computation queued for config scopes 'scopeId'..."));
    getEffectiveRuleDetails(backend, SCOPE_ID, "java:S106");

    backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(), List.of()));

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Evict cached rules definitions for connection 'connectionId'"));
  }

  @SonarLintTest
  void it_should_sync_when_credentials_are_updated(SonarLintTestHarness harness) {
    var client = harness.newFakeClient()
      .withCredentials(CONNECTION_ID, "user", "pw")
      .build();
    when(client.getClientLiveDescription()).thenReturn(this.getClass().getName());

    var introductionDate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey",
        project -> project.withBranch("main",
          branch -> branch.withTaintIssue("issueKey", "rule:key", "message", "author", "file/path", "OPEN", null, introductionDate, new TextRange(1, 2, 3, 4),
            RuleType.VULNERABILITY)))
      .start();

    server.getMockServer().stubFor(get("/api/system/status").willReturn(aResponse().withStatus(404)));

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage.withPlugin(TestPlugin.JAVA))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .withProjectSynchronization()
      .withFullSynchronization()
      .build(client);
    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Error while checking if soon unsupported"));

    server.registerSystemApiResponses();

    backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams(CONNECTION_ID));

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains(
      "Synchronizing connection 'connectionId' after credentials changed",
      "Synchronizing project branches for project 'projectKey'"));
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void it_should_notify_client_if_invalid_token(Integer status) {
    var client = newFakeClient()
      .withCredentials(CONNECTION_ID, "user", "pw")
      .build();
    when(client.getClientLiveDescription()).thenReturn(this.getClass().getName());

    var server = newSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("main"))
      .withResponseCode(status)
      .start();

    var backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage.withPlugin(TestPlugin.JAVA).withProject("projectKey"))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .withProjectSynchronization()
      .withFullSynchronization()
      .build(client);
    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Error during synchronization"));

    backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams(CONNECTION_ID));

    await().untilAsserted(() -> assertThat(client.getConnectionIdsWithInvalidToken()).containsExactly(CONNECTION_ID));
  }

  private EffectiveRuleDetailsDto getEffectiveRuleDetails(SonarLintTestRpcServer backend, String configScopeId, String ruleKey) {
    try {
      return backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, ruleKey, null)).get().details();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
