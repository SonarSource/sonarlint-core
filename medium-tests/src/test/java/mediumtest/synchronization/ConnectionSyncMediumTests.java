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
package mediumtest.synchronization;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class ConnectionSyncMediumTests {
  public static final String CONNECTION_ID = "connectionId";
  public static final String SCOPE_ID = "scopeId";
  private SonarLintRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void it_should_cache_extracted_rule_metadata_per_connection() {
    var client = newFakeClient()
      .withCredentials(CONNECTION_ID, "user", "pw")
      .build();
    when(client.getClientLiveDescription()).thenReturn(this.getClass().getName());

    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, storage -> storage.withPlugin(TestPlugin.JAVA))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .build(client);
    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Binding suggestion computation queued for config scopes 'scopeId'..."));

    assertThat(client.getLogMessages()).doesNotContain("Extracting rules metadata for connection 'connectionId'");

    // Trigger lazy initialization of the rules metadata
    getEffectiveRuleDetails(SCOPE_ID, "java:S106");
    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Extracting rules metadata for connection 'connectionId'"));

    // Second call should not trigger init as results are already cached
    client.clearLogs();

    getEffectiveRuleDetails(SCOPE_ID, "java:S106");
    assertThat(client.getLogs()).extracting(LogParams::getLevel, LogParams::getMessage).isEmpty();
  }

  @Test
  void it_should_evict_cache_when_connection_is_removed() {
    var client = newFakeClient()
      .withCredentials(CONNECTION_ID, "user", "pw")
      .build();
    when(client.getClientLiveDescription()).thenReturn(this.getClass().getName());

    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, storage -> storage.withPlugin(TestPlugin.JAVA))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .build(client);
    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Binding suggestion computation queued for config scopes 'scopeId'..."));
    getEffectiveRuleDetails(SCOPE_ID, "java:S106");

    backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(), List.of()));

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Evict cached rules definitions for connection 'connectionId'"));
  }

  @Test
  void it_should_sync_when_credentials_are_updated() {
    var client = newFakeClient()
      .withCredentials(CONNECTION_ID, "user", "pw")
      .build();
    when(client.getClientLiveDescription()).thenReturn(this.getClass().getName());

    var introductionDate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    var server = newSonarQubeServer()
      .withProject("projectKey",
        project -> project.withBranch("main",
          branch -> branch.withTaintIssue("issueKey", "rule:key", "message", "author", "file/path", "OPEN", null, introductionDate, new TextRange(1, 2, 3, 4),
            RuleType.VULNERABILITY)))
      .start();

    server.getMockServer().stubFor(get("/api/system/status").willReturn(aResponse().withStatus(401)));

    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage.withPlugin(TestPlugin.JAVA))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .withProjectSynchronization()
      .withFullSynchronization()
      .build(client);
    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Error during synchronization"));

    server.registerSystemApiResponses();

    backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams(CONNECTION_ID));

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains(
      "Synchronizing connection 'connectionId' after credentials changed",
      "[SYNC] Synchronizing project branches for project 'projectKey'"));
  }

  private EffectiveRuleDetailsDto getEffectiveRuleDetails(String configScopeId, String ruleKey) {
    return getEffectiveRuleDetails(configScopeId, ruleKey, null);
  }

  private EffectiveRuleDetailsDto getEffectiveRuleDetails(String configScopeId, String ruleKey, String contextKey) {
    try {
      return this.backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, ruleKey, contextKey)).get().details();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
