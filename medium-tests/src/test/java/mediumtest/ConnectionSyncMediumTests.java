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

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class ConnectionSyncMediumTests {
  private SonarLintRpcServer backend;
  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester(true);

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  @Disabled("The thread local logtester is not working well with lsp4j")
  void it_should_cache_extracted_rule_metadata_per_connection() {
    var client = newFakeClient()
      .withClientDescription(this.getClass().getName())
      .withCredentials("connectionId", "user", "pw")
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage.withJavaPlugin())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .build(client);
    await().untilAsserted(() -> assertThat(logTester.logs()).contains("Binding suggestion computation queued for config scopes 'scopeId'..."));

    assertThat(logTester.logs()).doesNotContain("Extracting rules metadata for connection 'connectionId'");

    // Trigger lazy initialization of the rules metadata
    getEffectiveRuleDetails("scopeId", "java:S106");
    await().untilAsserted(() -> assertThat(logTester.logs()).contains("Extracting rules metadata for connection 'connectionId'"));

    // Second call should not trigger init as results are already cached
    logTester.clear();

    getEffectiveRuleDetails("scopeId", "java:S106");
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void it_should_evict_cache_when_connection_is_removed() {
    var client = newFakeClient()
      .withClientDescription(this.getClass().getName())
      .withCredentials("connectionId", "user", "pw")
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage.withJavaPlugin())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .build(client);
    await().untilAsserted(() -> assertThat(logTester.logs()).contains("Binding suggestion computation queued for config scopes 'scopeId'..."));
    getEffectiveRuleDetails("scopeId", "java:S106");

    backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(), List.of()));

    await().untilAsserted(() -> assertThat(logTester.logs()).contains("Evict cached rules definitions for connection 'connectionId'"));
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
