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
package mediumtest.hotspots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.LogTestStartAndEnd;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.test.utils.server.sse.SSEServer;
import utils.TestPlugin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.newSonarQubeServer;
import static org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.newBackend;
import static org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.newFakeClient;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerSecurityHotspotFixture.aServerHotspot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static utils.AnalysisUtils.analyzeFileAndGetHotspots;

@ExtendWith(LogTestStartAndEnd.class)
class HotspotEventsMediumTests {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private static final SSEServer sseServer = new SSEServer();
  private SonarLintTestRpcServer backend;
  private ServerFixture.Server serverWithHotspots;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (serverWithHotspots != null) {
      serverWithHotspots.shutdown();
    }
    sseServer.stop();
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
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(readHotspots("connectionId", "projectKey", "branchName", "file/path"))
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
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(4)).untilAsserted(() -> assertThat(readHotspots("connectionId", "projectKey", "branchName", "file/path"))
        .isEmpty());
    }

    @Test
    void should_republish_hotspots_without_closed_one(@TempDir Path baseDir) {
      var filePath = createFile(baseDir, "Foo.java",
        "public class Foo {\n" +
          "\n" +
          "  void foo() {\n" +
          "    String password = \"blue\";\n" +
          "    String passwordD = \"red\";\n" +
          "  }\n" +
          "}\n");
      var fileUri = filePath.toUri();
      var connectionId = "connectionId";
      var branchName = "branchName";
      var projectKey = "projectKey";
      var serverHotspotKey1 = "myHotspotKey1";
      var serverHotspotKey2 = "myHotspotKey2";
      var client = newFakeClient()
        .withToken(connectionId, "token")
        .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
        .build();
      when(client.matchSonarProjectBranch(eq(CONFIG_SCOPE_ID), eq("main"), eq(Set.of("main", branchName)), any())).thenReturn(branchName);
      var introductionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS);
      serverWithHotspots = newSonarQubeServer("10.4")
        .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java").withActiveRule("java:S2068", activeRule -> activeRule
          .withSeverity(IssueSeverity.MAJOR)
        ))
        .withProject(projectKey,
          project -> project
            .withQualityProfile("qpKey")
            .withBranch(branchName,
              branch -> branch.withHotspot(serverHotspotKey1, hotspot -> hotspot
                  .withFilePath(baseDir.relativize(filePath).toString())
                  .withStatus(HotspotReviewStatus.TO_REVIEW)
                  .withVulnerabilityProbability(VulnerabilityProbability.HIGH)
                  .withTextRange(new TextRange(4, 11, 4, 19))
                  .withRuleKey("java:S2068")
                  .withMessage("'password' detected in this expression, review this potentially hard-coded password.")
                  .withCreationDate(introductionDate)
                  .withAuthor("author")
                )
                .withHotspot(serverHotspotKey2, hotspot -> hotspot
                  .withFilePath(baseDir.relativize(filePath).toString())
                  .withStatus(HotspotReviewStatus.TO_REVIEW)
                  .withVulnerabilityProbability(VulnerabilityProbability.HIGH)
                  .withTextRange(new TextRange(5, 11, 5, 20))
                  .withRuleKey("java:S2068")
                  .withMessage("'password' detected in this expression, review this potentially hard-coded password.")
                  .withCreationDate(introductionDate)
                  .withAuthor("author")
                )
            ))
        .withPlugin(TestPlugin.JAVA)
        .start();

      mockEvent(serverWithHotspots, projectKey,
        "event: SecurityHotspotClosed\n" +
          "data: {" +
          "    \"key\": \"myHotspotKey1\"," +
          "    \"projectKey\": \"projectKey\"," +
          "    \"filePath\": \"Foo.java\"" +
          "}\n\n"
      );
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withSecurityHotspotsEnabled()
        .withServerSentEventsEnabled()
        .withFullSynchronization()
        .withSonarQubeConnection(connectionId, serverWithHotspots)
        .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
        .build(client);

      await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));
      analyzeFileAndGetHotspots(fileUri, client, backend, CONFIG_SCOPE_ID);
      var raisedHotspots = client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID).get(fileUri);
      assertThat(raisedHotspots).hasSize(2);
      client.cleanRaisedHotspots();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());
      raisedHotspots = client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID).get(fileUri);

      assertThat(raisedHotspots).hasSize(1);
      var raisedHotspot = raisedHotspots.get(0);
      assertThat(raisedHotspot.getServerKey()).isEqualTo(serverHotspotKey2);
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
      sseServer.shouldSendServerEventOnce();

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
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readHotspots("connectionId", "projectKey", "branchName", "file/path"))
        .extracting(ServerHotspot::getKey, ServerHotspot::getAssignee)
        .containsOnly(tuple("AYhSN6mVrRF_krvNbHl1", "assigneeEmail")));
    }

    @Test
    void should_raise_hotspot_with_changed_data(@TempDir Path baseDir) {
      var filePath = createFile(baseDir, "Foo.java",
        "public class Foo {\n" +
          "\n" +
          "  void foo() {\n" +
          "    String password = \"blue\";\n" +
          "  }\n" +
          "}\n");
      var fileUri = filePath.toUri();
      var connectionId = "connectionId";
      var branchName = "branchName";
      var projectKey = "projectKey";
      var serverHotspotKey = "myHotspotKey";
      var client = newFakeClient()
        .withToken(connectionId, "token")
        .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
        .build();
      when(client.matchSonarProjectBranch(eq(CONFIG_SCOPE_ID), eq("main"), eq(Set.of("main", branchName)), any())).thenReturn(branchName);
      var introductionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS);
      serverWithHotspots = newSonarQubeServer("10.4")
        .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java").withActiveRule("java:S2068", activeRule -> activeRule
          .withSeverity(IssueSeverity.MAJOR)
        ))
        .withProject(projectKey,
          project -> project
            .withQualityProfile("qpKey")
            .withBranch(branchName,
              branch -> branch.withHotspot(serverHotspotKey, hotspot -> hotspot
                .withFilePath(baseDir.relativize(filePath).toString())
                .withStatus(HotspotReviewStatus.TO_REVIEW)
                .withVulnerabilityProbability(VulnerabilityProbability.HIGH)
                .withTextRange(new TextRange(4, 11, 4, 19))
                .withRuleKey("java:S2068")
                .withMessage("'password' detected in this expression, review this potentially hard-coded password.")
                .withCreationDate(introductionDate)
                .withAuthor("author")
              )
            ))
        .withPlugin(TestPlugin.JAVA)
        .start();

      mockEvent(serverWithHotspots, projectKey,
        "event: SecurityHotspotChanged\n" +
          "data: {" +
          "  \"key\": \"myHotspotKey\"," +
          "  \"projectKey\": \"projectKey\"," +
          "  \"updateDate\": 1685007187000," +
          "  \"status\": \"REVIEWED\"," +
          "  \"assignee\": \"assigneeEmail\"," +
          "  \"resolution\": \"SAFE\"," +
          "  \"filePath\": \"" + baseDir.relativize(filePath) + "\"" +
          "}\n\n"
      );
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withSecurityHotspotsEnabled()
        .withServerSentEventsEnabled()
        .withFullSynchronization()
        .withSonarQubeConnection(connectionId, serverWithHotspots)
        .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
        .build(client);

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));
      analyzeFileAndGetHotspots(fileUri, client, backend, CONFIG_SCOPE_ID);
      client.cleanRaisedHotspots();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID)).isNotEmpty());
      var raisedHotspots = client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID).get(fileUri);

      assertThat(raisedHotspots).hasSize(1);
      var raisedHotspot = raisedHotspots.get(0);
      assertThat(raisedHotspot.getServerKey()).isEqualTo(serverHotspotKey);
      assertThat(raisedHotspot.getStatus()).isEqualTo(HotspotStatus.SAFE);
    }
  }

  private static Path createFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return filePath;
  }

  private Collection<ServerHotspot> readHotspots(String connectionId, String projectKey, String branchName, String filePath) {
    return backend.getIssueStorageService().connection(connectionId).project(projectKey).findings().loadHotspots(branchName, Path.of(filePath));
  }

  private void mockEvent(ServerFixture.Server server, String projectKey, String eventPayload) {
    sseServer.startWithEvent(eventPayload);
    var sseServerUrl = sseServer.getUrl();
    server.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java")
      .willReturn(aResponse().proxiedFrom(sseServerUrl + "/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java")
        .withStatus(200)
      ));
  }
}
