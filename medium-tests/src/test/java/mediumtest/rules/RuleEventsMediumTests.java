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
package mediumtest.rules;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.commons.LogTestStartAndEnd;
import testutils.sse.SSEServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static testutils.AnalysisUtils.analyzeFileAndGetIssues;
import static testutils.AnalysisUtils.createFile;

@ExtendWith(LogTestStartAndEnd.class)
class RuleEventsMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private static final SSEServer sseServer = new SSEServer();
  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    sseServer.stop();
  }

  @Nested
  class WhenReceivingRuleSetChangedEvent {
    //write a test just like this one without the impacts
    @Test
    void it_should_create_the_ruleset_storage_if_does_not_exist_without_impacts() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: RuleSetChanged\n" +
        "data: {" +
        "\"projects\": [\"projectKey\", \"projectKey2\"]," +
        "\"deactivatedRules\": []," +
        "\"activatedRules\": [{" +
        "\"key\": \"java:S0000\"," +
        "\"language\": \"java\"," +
        "\"severity\": \"MAJOR\"," +
        "\"params\": [{" +
        "\"key\": \"key1\"," +
        "\"value\": \"value1\"" +
        "}]" +
        "}]," +
        "\"deactivatedRules\": [\"java:S4321\"]" +
        "}\n\n");
      var client = newFakeClient().build();
      when(client.matchSonarProjectBranch(eq("configScope"), any(), any(), any())).thenReturn("branchName");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .extractingByKey("java")
        .isEqualTo(Sonarlint.RuleSet.newBuilder()
          .addRule(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("java:S0000").setSeverity("MAJOR").putParams("key1", "value1").build()).build()));
    }

    @Test
    void it_should_create_the_ruleset_storage_if_does_not_exist() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: RuleSetChanged\n" +
        "data: {" +
        "\"projects\": [\"projectKey\", \"projectKey2\"]," +
        "\"deactivatedRules\": []," +
        "\"activatedRules\": [{" +
        "\"key\": \"java:S0000\"," +
        "\"language\": \"java\"," +
        "\"severity\": \"MAJOR\"," +
        "\"params\": [{" +
        "\"key\": \"key1\"," +
        "\"value\": \"value1\"" +
        "}]," +
        "\"impacts\": [{" +
        "\"softwareQuality\": \"SECURITY\"," +
        "\"severity\": \"HIGH\"" +
        "}]" +
        "}]" +
        "}\n\n");
      var client = newFakeClient().build();
      when(client.matchSonarProjectBranch(eq("configScope"), any(), any(), any())).thenReturn("branchName");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .extractingByKey("java")
        .isEqualTo(Sonarlint.RuleSet.newBuilder()
          .addRule(Sonarlint.RuleSet.ActiveRule.newBuilder()
            .setRuleKey("java:S0000")
            .setSeverity("MAJOR")
            .putParams("key1", "value1")
            .addOverriddenImpacts(Sonarlint.RuleSet.ActiveRule.newBuilder().addOverriddenImpactsBuilder()
              .setSoftwareQuality("SECURITY")
              .setSeverity("HIGH")
              .build())
            .build())
          .build()));
    }

    @Test
    void it_should_update_existing_rule_in_storage() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: RuleSetChanged\n" +
        "data: {" +
        "\"projects\": [\"projectKey\", \"projectKey2\"]," +
        "\"deactivatedRules\": []," +
        "\"activatedRules\": [{" +
        "\"key\": \"java:S0000\"," +
        "\"language\": \"java\"," +
        "\"severity\": \"MAJOR\"," +
        "\"params\": [{" +
        "\"key\": \"key1\"," +
        "\"value\": \"value1\"" +
        "}]" +
        "}]" +
        "}\n\n");
      var client = newFakeClient().build();
      when(client.matchSonarProjectBranch(eq("configScope"), any(), any(), any())).thenReturn("branchName");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .extractingByKey("java")
        .isEqualTo(Sonarlint.RuleSet.newBuilder()
          .addRule(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("java:S0000").setSeverity("MAJOR").putParams("key1", "value1").build()).build()));
    }

    @Test
    void it_should_add_rule_to_existing_ruleset_in_storage() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: RuleSetChanged\n" +
        "data: {" +
        "\"projects\": [\"projectKey\", \"projectKey2\"]," +
        "\"deactivatedRules\": []," +
        "\"activatedRules\": [{" +
        "\"key\": \"java:S0001\"," +
        "\"language\": \"java\"," +
        "\"severity\": \"MAJOR\"," +
        "\"params\": [{" +
        "\"key\": \"key1\"," +
        "\"value\": \"value1\"" +
        "}]" +
        "}]" +
        "}\n\n");
      var client = newFakeClient().build();
      when(client.matchSonarProjectBranch(eq("configScope"), any(), any(), any())).thenReturn("branchName");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .extractingByKey("java")
        .isEqualTo(Sonarlint.RuleSet.newBuilder()
          .addRule(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("java:S0000").setSeverity("INFO").build())
          .addRule(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("java:S0001").setSeverity("MAJOR").putParams("key1", "value1").build()).build()));
    }

    @Test
    void it_should_reanalyze_open_files_on_new_rules_enabled(@TempDir Path baseDir) {
      var filePath = createFile(baseDir, "Foo.java",
        "public class Foo {\n" +
          "\n" +
          "  void foo() {\n" +
          "    // TODO foo\n" +
          "    int i = 0;\n" +
          "  }\n" +
          "}\n");
      var fileUri = filePath.toUri();
      var connectionId = "connectionId";
      var branchName = "branchName";
      var projectKey = "projectKey";
      var client = newFakeClient()
        .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
        .build();
      when(client.matchSonarProjectBranch(eq(CONFIG_SCOPE_ID), eq("main"), eq(Set.of("main", branchName)), any())).thenReturn(branchName);
      var server = newSonarQubeServer()
        .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java")
          .withActiveRule("java:S1481", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR))
        )
        .withProject(projectKey,
          project -> project
            .withQualityProfile("qpKey"))
        .withPlugin(TestPlugin.JAVA)
        .start();
      mockEvent(server, projectKey,
        "event: RuleSetChanged\n" +
          "data: {" +
          "\"projects\": [\"projectKey\"]," +
          "\"deactivatedRules\": []," +
          "\"activatedRules\": [{" +
          "\"key\": \"java:S1135\"," +
          "\"language\": \"java\"," +
          "\"severity\": \"MAJOR\"," +
          "\"params\": []" +
          "}]" +
          "}\n\n"
      );
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withFullSynchronization()
        .withSonarQubeConnection(connectionId, server)
        .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
        .build(client);

      backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));
      var raisedIssues = analyzeFileAndGetIssues(fileUri, client, backend, CONFIG_SCOPE_ID);
      assertThat(raisedIssues).hasSize(1);
      client.cleanRaisedIssues();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).hasSize(2));
      raisedIssues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileUri);

      assertThat(raisedIssues).hasSize(2);
      assertThat(raisedIssues.stream().map(RaisedFindingDto::getRuleKey).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("java:S1135", "java:S1481");
    }

    @Test
    void it_should_add_rule_to_new_ruleset_in_existing_storage() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: RuleSetChanged\n" +
        "data: {" +
        "\"projects\": [\"projectKey\", \"projectKey2\"]," +
        "\"deactivatedRules\": []," +
        "\"activatedRules\": [{" +
        "\"key\": \"cs:S0000\"," +
        "\"language\": \"cs\"," +
        "\"severity\": \"MAJOR\"," +
        "\"params\": [{" +
        "\"key\": \"key1\"," +
        "\"value\": \"value1\"" +
        "}]" +
        "}]" +
        "}\n\n");
      var client = newFakeClient().build();
      when(client.matchSonarProjectBranch(eq("configScope"), any(), any(), any())).thenReturn("branchName");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .extractingByKey("cs")
        .isEqualTo(Sonarlint.RuleSet.newBuilder()
          .addRule(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("cs:S0000").setSeverity("MAJOR").putParams("key1", "value1").build()).build()));
    }

    @Test
    void it_should_remove_deactivated_rule_from_existing_storage() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: RuleSetChanged\n" +
        "data: {" +
        "\"projects\": [\"projectKey\", \"projectKey2\"]," +
        "\"activatedRules\": []," +
        "\"deactivatedRules\": [\"java:S0000\"]" +
        "}\n\n");
      var client = newFakeClient().build();
      when(client.matchSonarProjectBranch(eq("configScope"), any(), any(), any())).thenReturn("branchName");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO").withActiveRule("java:S0001", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .extractingByKey("java")
        .isEqualTo(Sonarlint.RuleSet.newBuilder().addRule(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("java:S0001").setSeverity("INFO").build()).build()));
    }

    @Test
    void it_should_remove_ruleset_from_storage_when_deactivating_last_rule() {
      var server = newSonarQubeServer("10.0")
        .withProject("projectKey",
          project -> project.withBranch("branchName"))
        .start();
      mockEvent(server, "projectKey", "event: RuleSetChanged\n" +
        "data: {" +
        "\"projects\": [\"projectKey\", \"projectKey2\"]," +
        "\"activatedRules\": []," +
        "\"deactivatedRules\": [\"java:S0000\"]" +
        "}\n\n");
      var client = newFakeClient().build();
      when(client.matchSonarProjectBranch(eq("configScope"), any(), any(), any())).thenReturn("branchName");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .isEmpty());
    }

    @Test
    void it_should_re_raise_issues_without_deactivated_rules(@TempDir Path baseDir) {
      var filePath = createFile(baseDir, "Foo.java",
        "public class Foo {\n" +
          "\n" +
          "  void foo() {\n" +
          "    // TODO foo\n" +
          "    int i = 0;\n" +
          "    String password = \"blue\";\n" +
          "    String ip = \"192.168.12.42\";\n" +
          "  }\n" +
          "}\n");
      var fileUri = filePath.toUri();
      var connectionId = "connectionId";
      var branchName = "branchName";
      var projectKey = "projectKey";
      var client = newFakeClient()
        .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
        .build();
      when(client.matchSonarProjectBranch(eq(CONFIG_SCOPE_ID), eq("main"), eq(Set.of("main", branchName)), any())).thenReturn(branchName);
      var server = newSonarQubeServer()
        .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java")
          .withActiveRule("java:S1481", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR))
          .withActiveRule("java:S1135", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR))
          .withActiveRule("java:S1313", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR))
          .withActiveRule("java:S2068", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR))
        )
        .withProject(projectKey,
          project -> project
            .withQualityProfile("qpKey"))
        .withPlugin(TestPlugin.JAVA)
        .start();

      mockEvent(server, projectKey,
        "event: RuleSetChanged\n" +
          "data: {" +
          "\"projects\": [\"projectKey\"]," +
          "\"activatedRules\": []," +
          "\"deactivatedRules\": [\"java:S1481\", \"java:S1313\"]" +
          "}\n\n"
      );
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSecurityHotspotsEnabled()
        .withFullSynchronization()
        .withSonarQubeConnection(connectionId, server)
        .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
        .build(client);

      await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));
      var raisedIssues = analyzeFileAndGetIssues(fileUri, client, backend, CONFIG_SCOPE_ID);
      assertThat(raisedIssues).hasSize(4);
      client.cleanRaisedIssues();
      client.cleanRaisedHotspots();
      sseServer.shouldSendServerEventOnce();

      await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());
      raisedIssues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileUri);
      var raisedHotspots = client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID).get(fileUri);

      assertThat(raisedIssues).hasSize(1);
      var raisedIssue = raisedIssues.get(0);
      assertThat(raisedIssue.getRuleKey()).isEqualTo("java:S1135");
      assertThat(raisedHotspots).hasSize(1);
      var raisedHotspot = raisedHotspots.get(0);
      assertThat(raisedHotspot.getRuleKey()).isEqualTo("java:S2068");
    }
  }

  private Map<String, Sonarlint.RuleSet> readRuleSets(String connectionId, String projectKey) {
    var path = backend.getStorageRoot().resolve(encodeForFs(connectionId)).resolve("projects").resolve(encodeForFs(projectKey)).resolve("analyzer_config.pb");
    if (path.toFile().exists()) {
      return ProtobufFileUtil.readFile(path, Sonarlint.AnalyzerConfiguration.parser()).getRuleSetsByLanguageKeyMap();
    }
    return Map.of();
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
