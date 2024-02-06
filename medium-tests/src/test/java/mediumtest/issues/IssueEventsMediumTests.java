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
package mediumtest.issues;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.storage.ServerIssueFixtures.aServerIssue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class IssueEventsMediumTests {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Nested
  class WhenReceivingIssueChangedEvent {
    @Test
    void it_should_update_issue_in_storage_with_new_resolution() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: IssueChanged\n" +
        "data: {" +
        "\"projectKey\": \"projectKey\"," +
        "\"issues\": [{" +
        "  \"issueKey\": \"key1\"," +
        "  \"branchName\": \"branchName\"" +
        "}]," +
        "\"resolved\": true" +
        "}\n\n");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server,
          storage -> storage.withProject("projectKey", project -> project.withMainBranch("branchName", branch -> branch.withIssue(aServerIssue("key1").open()))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readIssues("connectionId", "projectKey", "branchName", "file/path"))
        .extracting(ServerIssue::getKey, ServerIssue::isResolved)
        .containsOnly(tuple("key1", true)));
    }

    @Test
    void it_should_update_issue_in_storage_with_new_severity() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: IssueChanged\n" +
        "data: {" +
        "\"projectKey\": \"projectKey\"," +
        "\"issues\": [{" +
        "  \"issueKey\": \"key1\"," +
        "  \"branchName\": \"branchName\"" +
        "}]," +
        "\"userSeverity\": \"CRITICAL\"" +
        "}\n\n");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server,
          storage -> storage.withProject("projectKey",
            project -> project.withMainBranch("branchName", branch -> branch.withIssue(aServerIssue("key1").withSeverity(IssueSeverity.INFO)))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readIssues("connectionId", "projectKey", "branchName", "file/path"))
        .extracting(ServerIssue::getKey, ServerIssue::getUserSeverity)
        .containsOnly(tuple("key1", IssueSeverity.CRITICAL)));
    }

    @Test
    void it_should_update_issue_in_storage_with_new_type() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: IssueChanged\n" +
        "data: {" +
        "\"projectKey\": \"projectKey\"," +
        "\"issues\": [{" +
        "  \"issueKey\": \"key1\"," +
        "  \"branchName\": \"branchName\"" +
        "}]," +
        "\"userType\": \"BUG\"" +
        "}\n\n");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server,
          storage -> storage.withProject("projectKey",
            project -> project.withMainBranch("branchName", branch -> branch.withIssue(aServerIssue("key1").withType(RuleType.VULNERABILITY)))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readIssues("connectionId", "projectKey", "branchName", "file/path"))
        .extracting(ServerIssue::getKey, ServerIssue::getType)
        .containsOnly(tuple("key1", RuleType.BUG)));
    }
  }

  private List<ServerIssue<?>> readIssues(String connectionId, String projectKey, String branchName, String filePath) {
    return backend.getIssueStorageService().connection(connectionId).project(projectKey).findings().load(branchName, Path.of(filePath));
  }

  private static void mockEvent(ServerFixture.Server server, String projectKey, String eventPayload) {
    server.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java")
      .inScenario("Single event")
      .whenScenarioStateIs(STARTED)
      .willReturn(okForContentType("text/event-stream", eventPayload))
      .willSetStateTo("Event delivered"));
    // avoid later reconnection
    server.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java")
      .inScenario("Single event")
      .whenScenarioStateIs("Event delivered")
      .willReturn(notFound()));
  }
}
