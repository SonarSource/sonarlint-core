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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.scanner.protocol.Constants;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static mediumtest.fixtures.storage.ServerIssueFixtures.aServerIssue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS;
import static testutils.AnalysisUtils.analyzeFileAndGetIssue;
import static testutils.AnalysisUtils.createFile;

class IssueEventsMediumTests {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";

  private SonarLintTestRpcServer backend;
  private ServerFixture.Server serverWithIssues;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (serverWithIssues != null) {
      serverWithIssues.shutdown();
    }
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

    @Test
    void should_raise_issue_with_changed_rule_type(@TempDir Path baseDir) {
      var filePath = createFile(baseDir, "Foo.java",
        "public class Foo {\n}");
      var fileUri = filePath.toUri();
      var connectionId = "connectionId";
      var branchName = "branchName";
      var projectKey = "projectKey";
      var serverIssueKey = "myIssueKey";
      var client = newFakeClient()
        .withToken(connectionId, "token")
        .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
        .build();
      when(client.matchSonarProjectBranch(eq(CONFIG_SCOPE_ID), eq("main"), eq(Set.of("main", branchName)), any())).thenReturn(branchName);
      var introductionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS);
      serverWithIssues = newSonarQubeServer("10.4")
        .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java").withActiveRule("java:S2094", activeRule -> activeRule
          .withSeverity(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR)
        ))
        .withProject(projectKey,
          project -> project
            .withQualityProfile("qpKey")
            .withBranch(branchName,
              branch -> branch.withIssue(serverIssueKey, "java:S2094", "Remove this empty class, write its code or make it an \"interface\".",
                "author", baseDir.relativize(filePath).toString(), "1356c67d7ad1638d816bfb822dd2c25d", Constants.Severity.MAJOR, RuleType.CODE_SMELL,
                "OPEN", null, introductionDate, new TextRange(1, 13, 1, 16))
            ))
        .withPlugin(TestPlugin.JAVA)
        .start();

      serverWithIssues.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java,js")
        .inScenario("Single event")
        .whenScenarioStateIs(STARTED)
        .willReturn(okForContentType("text/event-stream", "event: IssueChanged\n" +
          "data: {" +
          "\"projectKey\": \"" + projectKey + "\"," +
          "\"issues\": [{" +
          "  \"issueKey\": \"myIssueKey\"," +
          "  \"branchName\": \"" + branchName + "\"" +
          "}]," +
          "\"userType\": \"BUG\"" +
          "}\n\n")
          // Add a delay to ensure event will arrive after the first analysis
          .withFixedDelay(5000))
        .willSetStateTo("Event delivered"));
      // avoid later reconnection
      serverWithIssues.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java,js")
        .inScenario("Single event")
        .whenScenarioStateIs("Event delivered")
        .willReturn(notFound()));
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withFullSynchronization()
        .withSonarQubeConnection(connectionId, serverWithIssues)
        .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
        .build(client);

      await().atMost(Duration.ofMinutes(2)).untilAsserted(() -> assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));
      analyzeFileAndGetIssue(fileUri, client, backend, CONFIG_SCOPE_ID);
      client.cleanRaisedIssues();

      await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());
      var raisedIssues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileUri);

      assertThat(raisedIssues).isNotEmpty();
      var raisedIssueDto = raisedIssues.get(0);
      assertThat(raisedIssueDto.getServerKey()).isEqualTo(serverIssueKey);
      assertThat(raisedIssueDto.getType()).isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.BUG);
    }

    @Test
    void should_raise_issue_with_changed_resolution(@TempDir Path baseDir) {
      var filePath = createFile(baseDir, "Foo.java",
        "public class Foo {\n}");
      var fileUri = filePath.toUri();
      var connectionId = "connectionId";
      var branchName = "branchName";
      var projectKey = "projectKey";
      var serverIssueKey = "myIssueKey";
      var client = newFakeClient()
        .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
        .build();
      when(client.matchSonarProjectBranch(eq(CONFIG_SCOPE_ID), eq("main"), eq(Set.of("main", branchName)), any())).thenReturn(branchName);
      var introductionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS);
      serverWithIssues = newSonarQubeServer("10.4")
        .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java").withActiveRule("java:S2094", activeRule -> activeRule
          .withSeverity(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR)
        ))
        .withProject(projectKey,
          project -> project
            .withQualityProfile("qpKey")
            .withBranch(branchName,
              branch -> branch.withIssue(serverIssueKey, "java:S2094", "Remove this empty class, write its code or make it an \"interface\".",
                "author", baseDir.relativize(filePath).toString(), "1356c67d7ad1638d816bfb822dd2c25d", Constants.Severity.MAJOR, RuleType.CODE_SMELL,
                "OPEN", null, introductionDate, new TextRange(1, 13, 1, 16))
            ))
        .withPlugin(TestPlugin.JAVA)
        .start();

      serverWithIssues.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java,js")
        .inScenario("Single event")
        .whenScenarioStateIs(STARTED)
        .willReturn(okForContentType("text/event-stream", "event: IssueChanged\n" +
          "data: {" +
          "\"projectKey\": \"" + projectKey + "\"," +
          "\"issues\": [{" +
          "  \"issueKey\": \"myIssueKey\"," +
          "  \"branchName\": \"" + branchName + "\"" +
          "}]," +
          "\"resolved\": \"true\"" +
          "}\n\n")
          // Add a delay to ensure event will arrive after the first analysis
          .withFixedDelay(5000))
        .willSetStateTo("Event delivered"));
      // avoid later reconnection
      serverWithIssues.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java,js")
        .inScenario("Single event")
        .whenScenarioStateIs("Event delivered")
        .willReturn(notFound()));
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withFullSynchronization()
        .withSonarQubeConnection(connectionId, serverWithIssues)
        .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
        .build(client);

      await().atMost(Duration.ofMinutes(2)).untilAsserted(() -> assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));
      analyzeFileAndGetIssue(fileUri, client, backend, CONFIG_SCOPE_ID);
      client.cleanRaisedIssues();

      await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());
      var raisedIssues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileUri);

      assertThat(raisedIssues).isNotEmpty();
      var raisedIssueDto = raisedIssues.get(0);
      assertThat(raisedIssueDto.getServerKey()).isEqualTo(serverIssueKey);
      assertThat(raisedIssueDto.isResolved()).isTrue();
    }

    @Test
    void should_raise_issue_with_changed_severity(@TempDir Path baseDir) {
      var filePath = createFile(baseDir, "Foo.java",
        "public class Foo {\n}");
      var fileUri = filePath.toUri();
      var connectionId = "connectionId";
      var branchName = "branchName";
      var projectKey = "projectKey";
      var serverIssueKey = "myIssueKey";
      var client = newFakeClient()
        .withToken(connectionId, "token")
        .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
        .build();
      when(client.matchSonarProjectBranch(eq(CONFIG_SCOPE_ID), eq("main"), eq(Set.of("main", branchName)), any())).thenReturn(branchName);
      var introductionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS);
      serverWithIssues = newSonarQubeServer("10.4")
        .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java").withActiveRule("java:S2094", activeRule -> activeRule
          .withSeverity(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR)
        ))
        .withProject(projectKey,
          project -> project
            .withQualityProfile("qpKey")
            .withBranch(branchName,
              branch -> branch.withIssue(serverIssueKey, "java:S2094", "Remove this empty class, write its code or make it an \"interface\".",
                "author", baseDir.relativize(filePath).toString(), "1356c67d7ad1638d816bfb822dd2c25d", Constants.Severity.MAJOR, RuleType.CODE_SMELL,
                "OPEN", null, introductionDate, new TextRange(1, 13, 1, 16))
            ))
        .withPlugin(TestPlugin.JAVA)
        .start();

      serverWithIssues.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java,js")
        .inScenario("Single event")
        .whenScenarioStateIs(STARTED)
        .willReturn(okForContentType("text/event-stream", "event: IssueChanged\n" +
          "data: {" +
          "\"projectKey\": \"" + projectKey + "\"," +
          "\"issues\": [{" +
          "  \"issueKey\": \"myIssueKey\"," +
          "  \"branchName\": \"" + branchName + "\"" +
          "}]," +
          "\"userSeverity\": \"MINOR\"" +
          "}\n\n")
          // Add a delay to ensure event will arrive after the first analysis
          .withFixedDelay(5000))
        .willSetStateTo("Event delivered"));
      // avoid later reconnection
      serverWithIssues.getMockServer().stubFor(get("/api/push/sonarlint_events?projectKeys=" + projectKey + "&languages=java,js")
        .inScenario("Single event")
        .whenScenarioStateIs("Event delivered")
        .willReturn(notFound()));
      backend = newBackend()
        .withEnabledLanguageInStandaloneMode(JS)
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withFullSynchronization()
        .withSonarQubeConnection(connectionId, serverWithIssues)
        .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
        .build(client);

      await().atMost(Duration.ofMinutes(2)).untilAsserted(() -> assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));
      analyzeFileAndGetIssue(fileUri, client, backend, CONFIG_SCOPE_ID);
      client.cleanRaisedIssues();

      await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());
      var raisedIssues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileUri);

      assertThat(raisedIssues).isNotEmpty();
      var raisedIssueDto = raisedIssues.get(0);
      assertThat(raisedIssueDto.getServerKey()).isEqualTo(serverIssueKey);
      assertThat(raisedIssueDto.getSeverity()).isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MINOR);
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
