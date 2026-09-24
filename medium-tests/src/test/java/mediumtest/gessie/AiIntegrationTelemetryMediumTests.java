/*
 * SonarLint Core - Medium Tests
 * Copyright (C) SonarSource Sàrl
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
package mediumtest.gessie;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationInspectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryMigrationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiAgentIntegrationStateObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationCliStateObservedParams;
import org.sonarsource.sonarlint.core.telemetry.gessie.GessieSpringConfig;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AiIntegrationTelemetryMediumTests {
  @RegisterExtension
  static WireMockExtension endpoint = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @BeforeEach
  void setUp() {
    System.setProperty(GessieSpringConfig.PROPERTY_GESSIE_API_KEY, "value");
    endpoint.stubFor(post("/ide").willReturn(aResponse().withStatus(202)));
  }

  @AfterEach
  void tearDown() {
    System.clearProperty(GessieSpringConfig.PROPERTY_GESSIE_API_KEY);
    System.clearProperty(GessieSpringConfig.PROPERTY_GESSIE_ENDPOINT);
  }

  @SonarLintTest
  void should_round_trip_all_three_notifications_with_exact_payloads(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withProductKey("vscode").withGessieTelemetryEnabled(endpoint.baseUrl()).start();
    var telemetry = backend.getTelemetryService();
    reportAll(telemetry);

    await().untilAsserted(() -> assertThat(aiEvents()).hasSize(3));
    var action = event("IdeAiIntegrationAction");
    var cli = event("IdeAiIntegrationCliStateObserved");
    var agent = event("IdeAiAgentIntegrationStateObserved");
    for (var event : List.of(action, cli, agent)) {
      assertThat(event.keySet()).containsExactlyInAnyOrder("metadata", "event_payload");
      var metadata = event.getAsJsonObject("metadata");
      assertThat(metadata.keySet()).containsExactlyInAnyOrder("event_id", "event_type", "event_timestamp", "event_version", "source");
      assertThat(UUID.fromString(metadata.get("event_id").getAsString())).isNotNull();
      assertThat(metadata.get("event_version").getAsString()).isEqualTo("1");
      assertThat(metadata.get("event_timestamp").getAsJsonPrimitive().isString()).isTrue();
      assertThat(Long.parseLong(metadata.get("event_timestamp").getAsString())).isPositive();
      assertThat(metadata.get("source")).isEqualTo(JsonParser.parseString("{\"domain\":\"VSCode\"}"));
      var payload = event.getAsJsonObject("event_payload");
      assertThat(payload.get("machine_id").getAsString()).isNotBlank();
      assertThat(payload.get("ide_installation_id").getAsString()).isEqualTo(backend.telemetryFileContent().ideInstallationId());
      payload.remove("machine_id");
      payload.remove("ide_installation_id");
    }
    assertThat(action.get("event_payload")).isEqualTo(JsonParser.parseString("""
      {"action":"INSTALL_CLI","status":"STARTED","failure_category":null,"agent":null,"host":"VSCODE"}
      """));
    assertThat(cli.get("event_payload")).isEqualTo(JsonParser.parseString("""
      {"installation_status":"INSTALLED","authentication_status":"AUTHENTICATED",
      "host":"VSCODE"}
      """));
    assertThat(agent.get("event_payload")).isEqualTo(JsonParser.parseString("""
      {"agent":"CURSOR","detection_sources":["IDE","CLI"],"standalone_mcp_state":"UNKNOWN","host":"VSCODE"}
      """));
  }

  @SonarLintTest
  void gessie_only_should_obey_runtime_consent_without_fetching_legacy_attributes(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend().withGessieTelemetryEnabled(endpoint.baseUrl())
      .withTelemetryMigration(new TelemetryMigrationDto(OffsetDateTime.now().minusDays(2), 1, false)).start(client);
    var telemetry = backend.getTelemetryService();
    assertThat(telemetry.getStatus().join().isEnabled()).isFalse();
    reportAll(telemetry);
    assertNoAiEvents();

    telemetry.enableTelemetry();
    await().untilAsserted(() -> assertThat(telemetry.getStatus().join().isEnabled()).isTrue());
    reportAll(telemetry);
    await().untilAsserted(() -> assertThat(aiEvents()).hasSize(3));

    telemetry.disableTelemetry();
    await().untilAsserted(() -> assertThat(telemetry.getStatus().join().isEnabled()).isFalse());
    reportAll(telemetry);
    await().during(Duration.ofMillis(200)).untilAsserted(() -> assertThat(aiEvents()).hasSize(3));
    assertThat(backend.telemetryFileContent().enabled()).isFalse();
    verify(client, never()).getTelemetryLiveAttributes();
  }

  @SonarLintTest
  void should_drop_reports_without_gessie_capability(SonarLintTestHarness harness) {
    System.setProperty(GessieSpringConfig.PROPERTY_GESSIE_ENDPOINT, endpoint.baseUrl());
    var backend = harness.newBackend().start();
    reportAll(backend.getTelemetryService());
    await().during(Duration.ofMillis(200)).untilAsserted(() -> assertThat(endpoint.getAllServeEvents()).isEmpty());
  }

  @SonarLintTest
  void should_emit_each_load_report_independently_of_functional_operations(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withGessieTelemetryEnabled(endpoint.baseUrl()).start();
    var ai = backend.getAiAgentService();
    ai.getIntegrationState(new GetAiIntegrationStateParams(AiIntegrationHost.VSCODE, List.of(AiAgent.CURSOR), AiIntegrationScope.GLOBAL, null)).join();
    ai.prepareInstallCommand().join();
    ai.inspectMcpConfiguration(new McpConfigurationInspectionParams(AiAgent.CURSOR, null)).join();
    ai.planMcpConfigurationUpdate(new McpConfigurationUpdateParams(AiAgent.CURSOR, null, "{\"command\":\"sonar\"}")).join();
    assertNoAiEvents();

    var telemetry = backend.getTelemetryService();
    telemetry.aiIntegrationCliStateObserved(cliObservation());
    telemetry.aiIntegrationCliStateObserved(cliObservation());
    await().untilAsserted(() -> assertThat(aiEvents()).hasSize(2));
    assertThat(aiEvents()).extracting(event -> event.getAsJsonObject("metadata").get("event_id").getAsString()).doesNotHaveDuplicates();
    assertThat(aiEvents()).allSatisfy(event -> assertThat(event.getAsJsonObject("metadata").get("event_type").getAsString())
      .isEqualTo("Analytics.Editor.IdeAiIntegrationCliStateObserved"));
  }

  @SonarLintTest
  void should_drop_malformed_deserialized_reports_and_accept_valid_cli_observation(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withGessieTelemetryEnabled(endpoint.baseUrl()).start();
    var telemetry = backend.getTelemetryService();
    var gson = new Gson();
    telemetry.aiIntegrationAction(gson.fromJson("{}", AiIntegrationActionParams.class));
    telemetry.aiIntegrationCliStateObserved(gson.fromJson("""
      {"installationStatus":"INSTALLED","host":"VSCODE"}
      """, AiIntegrationCliStateObservedParams.class));
    telemetry.aiAgentIntegrationStateObserved(gson.fromJson("""
      {"agent":"CURSOR","detectionSources":[null],"standaloneMcpState":"UNKNOWN","host":"VSCODE"}
      """, AiAgentIntegrationStateObservedParams.class));
    assertNoAiEvents();
    telemetry.aiIntegrationCliStateObserved(new AiIntegrationCliStateObservedParams(
      CliInstallationStatus.NOT_INSTALLED, CliAuthenticationStatus.UNKNOWN, AiIntegrationHost.VSCODE));
    await().untilAsserted(() -> assertThat(aiEvents()).hasSize(1));
  }

  private static void reportAll(TelemetryRpcService telemetry) {
    telemetry.aiIntegrationAction(new AiIntegrationActionParams(AiIntegrationAction.INSTALL_CLI, AiIntegrationActionStatus.STARTED, null, null, AiIntegrationHost.VSCODE));
    telemetry.aiIntegrationCliStateObserved(cliObservation());
    telemetry.aiAgentIntegrationStateObserved(new AiAgentIntegrationStateObservedParams(AiAgent.CURSOR,
      List.of(AiAgentDetectionSource.CLI, AiAgentDetectionSource.IDE, AiAgentDetectionSource.CLI), McpConfigurationState.UNKNOWN,
      AiIntegrationHost.VSCODE));
  }

  private static AiIntegrationCliStateObservedParams cliObservation() {
    return new AiIntegrationCliStateObservedParams(CliInstallationStatus.INSTALLED,
      CliAuthenticationStatus.AUTHENTICATED, AiIntegrationHost.VSCODE);
  }

  private static void assertNoAiEvents() {
    await().during(Duration.ofMillis(200)).untilAsserted(() -> assertThat(aiEvents()).isEmpty());
  }

  private static List<JsonObject> aiEvents() {
    return endpoint.getAllServeEvents().stream()
      .map(event -> JsonParser.parseString(event.getRequest().getBodyAsString()).getAsJsonObject())
      .filter(event -> event.getAsJsonObject("metadata").get("event_type").getAsString().startsWith("Analytics.Editor.IdeAi"))
      .toList();
  }

  private static JsonObject event(String type) {
    return aiEvents().stream().filter(event -> event.getAsJsonObject("metadata").get("event_type").getAsString().equals("Analytics.Editor." + type))
      .findFirst().orElseThrow();
  }
}
