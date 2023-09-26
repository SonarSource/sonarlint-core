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
package mediumtest.issues;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.embedded.server.ShowIssueRequestHandler;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static mediumtest.fixtures.storage.ServerIssueFixtures.aServerIssue;
import static org.assertj.core.api.Assertions.assertThat;

class ShowIssueMediumTests {

  private SonarLintTestBackend backend;
  private ServerFixture.Server server;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
      server = null;
    }
  }

  @Test
  void it_should_update_the_telemetry_on_show_issue() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey",
          project -> project.withBranch("main",
            branch -> branch.withIssue(
              aServerIssue("myIssueKey")
                .withRuleKey("rule:key")
                .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
                .withIntroductionDate(Instant.EPOCH.plusSeconds(1))
                .withType(RuleType.BUG))))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .build();

    var showIssueRequestHandler = new ShowIssueRequestHandler(newFakeClient().build(), backend.getConnectionConfigurationRepository(),
      backend.getConfigurationServiceImpl(), backend.getBindingSuggestionProviderImpl(), backend.getServerApiProvider(),
      backend.getTelemetryServiceImpl());

    showIssueRequestHandler.showIssue(new ShowIssueRequestHandler.ShowIssueQuery(server.baseUrl(), "projectKey", "issueKey"));

    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"showIssueRequestsCount\":1");
  }
}
