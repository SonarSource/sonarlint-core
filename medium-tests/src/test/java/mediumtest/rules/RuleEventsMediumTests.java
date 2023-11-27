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
package mediumtest.rules;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

class RuleEventsMediumTests {

  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Nested
  class WhenReceivingRuleSetChangedEvent {
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
        "}]" +
        "}]," +
        "\"deactivatedRules\": [\"java:S4321\"]" +
        "}\n\n");
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .extractingByKey("java")
        .isEqualTo(Sonarlint.RuleSet.newBuilder()
          .addRule(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("java:S0000").setSeverity("MAJOR").putParams("key1", "value1").build()).build()));
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
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

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
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .extractingByKey("java")
        .isEqualTo(Sonarlint.RuleSet.newBuilder()
          .addRule(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("java:S0000").setSeverity("INFO").build())
          .addRule(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("java:S0001").setSeverity("MAJOR").putParams("key1", "value1").build()).build()));
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
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

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
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO").withActiveRule("java:S0001", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

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
      backend = newBackend()
        .withExtraEnabledLanguagesInConnectedMode(JAVA)
        .withServerSentEventsEnabled()
        .withSonarQubeConnection("connectionId", server, storage -> storage.withProject("projectKey",
          project -> project.withRuleSet("java",
            ruleSet -> ruleSet.withActiveRule("java:S0000", "INFO"))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(readRuleSets("connectionId", "projectKey"))
        .isEmpty());
    }
  }

  private Map<String, Sonarlint.RuleSet> readRuleSets(String connectionId, String projectKey) {
    return ProtobufFileUtil.readFile(backend.getStorageRoot().resolve(encodeForFs(connectionId)).resolve("projects").resolve(encodeForFs(projectKey)).resolve("analyzer_config.pb"),
      Sonarlint.AnalyzerConfiguration.parser()).getRuleSetsByLanguageKeyMap();
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
