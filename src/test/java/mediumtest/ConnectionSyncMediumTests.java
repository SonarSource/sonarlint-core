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
package mediumtest;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ConnectionSyncMediumTests {
  private SonarLintBackend backend;
  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester(true);

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void it_should_cache_extracted_rule_metadata_per_connection() {
    backend = newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage.withJavaPlugin())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withEnabledLanguage(Language.JAVA)
      .build();

    await().untilAsserted(() -> assertThat(logTester.logs()).contains("Binding suggestion computation queued for config scopes 'scopeId'..."));

    assertThat(logTester.logs()).doesNotContain("Extracting rules metadata for connection 'connectionId'");

    // Trigger lazy initialization of the rules metadata
    getEffectiveRuleDetails("scopeId", "java:S106");
    assertThat(logTester.logs()).contains("Extracting rules metadata for connection 'connectionId'");

    // Second call should not trigger init as results are already cached
    logTester.clear();

    getEffectiveRuleDetails("scopeId", "java:S106");
    assertThat(logTester.logs()).isEmpty();

    // Removing and adding the connection back should evict the cache
    backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(), List.of()));
    await().untilAsserted( () -> assertThat(logTester.logs()).contains("Evict cached rules definitions for connection 'connectionId'"));
    backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionId", "http://foo", true)), List.of()));

    logTester.clear();

    getEffectiveRuleDetails("scopeId", "java:S106");
    assertThat(logTester.logs()).contains("Extracting rules metadata for connection 'connectionId'");
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
