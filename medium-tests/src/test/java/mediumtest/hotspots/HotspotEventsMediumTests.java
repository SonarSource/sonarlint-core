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
package mediumtest.hotspots;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.storage.ServerSecurityHotspotFixture.aServerHotspot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class HotspotEventsMediumTests {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Nested
  class WhenReceivingSecurityHotspotRaisedEvent {
    @Test
    void it_should_add_hotspot_in_storage() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: SecurityHotspotRaised\n" +
        "data: {" +
        "  \"status\": \"TO_REVIEW\"," +
        "  \"vulnerabilityProbability\": \"MEDIUM\"," +
        "  \"creationDate\": 1685006550000," +
        "  \"mainLocation\": {" +
        "    \"filePath\": \"file/path\"," +
        "    \"message\": \"Make sure that using this pseudorandom number generator is safe here.\"," +
        "    \"textRange\": {" +
        "      \"startLine\": 12," +
        "      \"startLineOffset\": 29," +
        "      \"endLine\": 12," +
        "      \"endLineOffset\": 36," +
        "      \"hash\": \"43b5c9175984c071f30b873fdce0a000\"" +
        "    }" +
        "  }," +
        "  \"ruleKey\": \"java:S2245\"," +
        "  \"key\": \"AYhSN6mVrRF_krvNbHl1\"," +
        "  \"projectKey\": \"projectKey\"," +
        "  \"branch\": \"branchName\"" +
        "}\n\n");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server,
          storage -> storage.withProject("projectKey", project -> project.withMainBranch("branchName")))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readHotspots("connectionId", "projectKey", "branchName", "file/path"))
        .extracting(ServerHotspot::getKey)
        .containsOnly("AYhSN6mVrRF_krvNbHl1"));
    }
  }

  @Nested
  class WhenReceivingSecurityHotspotClosedEvent {
    @Test
    void it_should_remove_hotspot_from_storage() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: SecurityHotspotClosed\n" +
        "data: {" +
        "    \"key\": \"hotspotKey\"," +
        "    \"projectKey\": \"projectKey\"," +
        "    \"filePath\": \"file/path\"" +
        "}\n\n");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server,
          storage -> storage.withProject("projectKey", project -> project.withMainBranch("branchName", branch -> branch.withHotspot(aServerHotspot("hotspotKey")))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(4)).untilAsserted(() -> assertThat(readHotspots("connectionId", "projectKey", "branchName", "file/path"))
        .isEmpty());
    }
  }

  @Nested
  class WhenReceivingSecurityHotspotChangedEvent {
    @Test
    void it_should_update_hotspot_in_storage_when_changing_status() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: SecurityHotspotChanged\n" +
        "data: {" +
        "  \"key\": \"AYhSN6mVrRF_krvNbHl1\"," +
        "  \"projectKey\": \"projectKey\"," +
        "  \"updateDate\": 1685007187000," +
        "  \"status\": \"REVIEWED\"," +
        "  \"assignee\": \"assigneeEmail\"," +
        "  \"resolution\": \"SAFE\"," +
        "  \"filePath\": \"file/path\"" +
        "}\n\n");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server,
          storage -> storage.withProject("projectKey",
            project -> project.withMainBranch("branchName", branch -> branch.withHotspot(aServerHotspot("AYhSN6mVrRF_krvNbHl1").withStatus(HotspotReviewStatus.TO_REVIEW)))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readHotspots("connectionId", "projectKey", "branchName", "file/path"))
        .extracting(ServerHotspot::getKey, ServerHotspot::getStatus)
        .containsOnly(tuple("AYhSN6mVrRF_krvNbHl1", HotspotReviewStatus.SAFE)));
    }

    @Test
    void it_should_update_hotspot_in_storage_when_changing_assignee() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: SecurityHotspotChanged\n" +
        "data: {" +
        "  \"key\": \"AYhSN6mVrRF_krvNbHl1\"," +
        "  \"projectKey\": \"projectKey\"," +
        "  \"updateDate\": 1685007187000," +
        "  \"status\": \"REVIEWED\"," +
        "  \"assignee\": \"assigneeEmail\"," +
        "  \"resolution\": \"SAFE\"," +
        "  \"filePath\": \"file/path\"" +
        "}\n\n");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server,
          storage -> storage.withProject("projectKey",
            project -> project.withMainBranch("branchName", branch -> branch.withHotspot(aServerHotspot("AYhSN6mVrRF_krvNbHl1").withAssignee("previousAssignee")))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readHotspots("connectionId", "projectKey", "branchName", "file/path"))
        .extracting(ServerHotspot::getKey, ServerHotspot::getAssignee)
        .containsOnly(tuple("AYhSN6mVrRF_krvNbHl1", "assigneeEmail")));
    }
  }

  private Collection<ServerHotspot> readHotspots(String connectionId, String projectKey, String branchName, String filePath) {
    return backend.getIssueStorageService().connection(connectionId).project(projectKey).findings().loadHotspots(branchName, filePath);
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
