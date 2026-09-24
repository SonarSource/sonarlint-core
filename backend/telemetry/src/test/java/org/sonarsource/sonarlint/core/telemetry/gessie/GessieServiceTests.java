/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.gessie;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiAgentIntegrationStateObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationCliStateObservedParams;
import org.sonarsource.sonarlint.core.telemetry.MachineIdProvider;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorageManager;
import org.sonarsource.sonarlint.core.telemetry.common.TelemetryUserSetting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GessieServiceTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final Gson RPC_GSON = new Gson();
  private static final String ACTION = """
    {"action":"INSTALL_CLI","status":"FAILED","host":"VSCODE"}
    """;
  private static final String CLI = """
    {"installationStatus":"INSTALLED","authenticationStatus":"UNVERIFIED",
    "host":"VSCODE"}
    """;
  private static final String AGENT = """
    {"agent":"CURSOR","detectionSources":["CLI","IDE","CLI"],
    "standaloneMcpState":"UNKNOWN","host":"VSCODE"}
    """;
  private final HttpClient http = mock(HttpClient.class);
  private final TelemetryUserSetting setting = mock(TelemetryUserSetting.class);
  private final MachineIdProvider machineId = mock(MachineIdProvider.class);
  private final TelemetryLocalStorageManager storage = mock(TelemetryLocalStorageManager.class);
  private GessieService service;

  @BeforeEach
  void setUp() {
    when(setting.isTelemetryEnabledByUser()).thenReturn(true);
    when(machineId.getMachineId()).thenReturn("shared-machine");
    when(storage.ideInstallationId()).thenReturn("ide-installation");
    when(http.postAsync(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(mock(HttpClient.Response.class)));
    service = newService(Set.of(BackendCapability.GESSIE_TELEMETRY));
  }

  @Test
  void should_send_only_allowlisted_action_fields_and_common_metadata() {
    var input = JsonParser.parseString(ACTION).getAsJsonObject();
    input.addProperty("machine_id", "forged");
    input.addProperty("ide_installation_id", "forged");
    input.addProperty("event_type", "forged");
    input.addProperty("path", "private-directory");
    input.addProperty("diagnostic", "private-content");
    input.addProperty("scope", "PROJECT");
    service.aiIntegrationAction(RPC_GSON.fromJson(input, AiIntegrationActionParams.class));

    var event = uploadedEvent();
    assertMetadata(event, "IdeAiIntegrationAction");
    assertThat(event.get("event_payload")).isEqualTo(JsonParser.parseString("""
      {"action":"INSTALL_CLI","status":"FAILED","failure_category":"UNKNOWN","agent":null,
      "host":"VSCODE","machine_id":"shared-machine","ide_installation_id":"ide-installation"}
      """));
  }

  @Test
  void should_send_cli_observation_with_nullable_identity() {
    when(machineId.getMachineId()).thenReturn(null);
    when(storage.ideInstallationId()).thenReturn(null);
    service.aiIntegrationCliStateObserved(RPC_GSON.fromJson(CLI, AiIntegrationCliStateObservedParams.class));

    var event = uploadedEvent();
    assertMetadata(event, "IdeAiIntegrationCliStateObserved");
    assertThat(event.get("event_payload")).isEqualTo(JsonParser.parseString("""
      {"installation_status":"INSTALLED","authentication_status":"UNVERIFIED",
      "host":"VSCODE","machine_id":null,"ide_installation_id":null}
      """));
  }

  @Test
  void should_send_agent_observation_with_unique_ordered_sources() {
    service.aiAgentIntegrationStateObserved(RPC_GSON.fromJson(AGENT, AiAgentIntegrationStateObservedParams.class));

    var event = uploadedEvent();
    assertMetadata(event, "IdeAiAgentIntegrationStateObserved");
    assertThat(event.get("event_payload")).isEqualTo(JsonParser.parseString("""
      {"agent":"CURSOR","detection_sources":["IDE","CLI"],"standalone_mcp_state":"UNKNOWN",
      "host":"VSCODE","machine_id":"shared-machine","ide_installation_id":"ide-installation"}
      """));
  }

  @ParameterizedTest
  @ValueSource(strings = {"STARTED", "SUCCEEDED", "CANCELLED", "UNKNOWN"})
  void should_clear_failure_category_for_nonfailed_actions(String status) {
    var input = JsonParser.parseString(ACTION).getAsJsonObject();
    input.addProperty("status", status);
    input.addProperty("failureCategory", "TERMINAL_ERROR");
    input.addProperty("agent", "CODEX");
    service.aiIntegrationAction(RPC_GSON.fromJson(input, AiIntegrationActionParams.class));

    var payload = uploadedEvent().getAsJsonObject("event_payload");
    assertThat(payload.get("failure_category").isJsonNull()).isTrue();
    assertThat(payload.get("agent").getAsString()).isEqualTo("CODEX");
  }

  @Test
  void should_preserve_failed_category() {
    service.aiIntegrationAction(RPC_GSON.fromJson(ACTION.replace("\"FAILED\"", "\"FAILED\",\"failureCategory\":\"UNSUPPORTED\""), AiIntegrationActionParams.class));
    assertThat(uploadedEvent().getAsJsonObject("event_payload").get("failure_category").getAsString()).isEqualTo("UNSUPPORTED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"action", "status", "host"})
  void should_drop_missing_and_invalid_action_fields_after_deserialization(String field) {
    var input = JsonParser.parseString(ACTION).getAsJsonObject();
    input.remove(field);
    service.aiIntegrationAction(RPC_GSON.fromJson(input, AiIntegrationActionParams.class));
    input.addProperty(field, "NOT_AN_ENUM_VALUE");
    service.aiIntegrationAction(RPC_GSON.fromJson(input, AiIntegrationActionParams.class));
    verifyNoUpload();
  }

  @ParameterizedTest
  @ValueSource(strings = {"installationStatus", "authenticationStatus", "host"})
  void should_drop_missing_cli_fields_after_deserialization(String field) {
    var input = JsonParser.parseString(CLI).getAsJsonObject();
    input.remove(field);
    service.aiIntegrationCliStateObserved(RPC_GSON.fromJson(input, AiIntegrationCliStateObservedParams.class));
    input.addProperty(field, "NOT_AN_ENUM_VALUE");
    service.aiIntegrationCliStateObserved(RPC_GSON.fromJson(input, AiIntegrationCliStateObservedParams.class));
    verifyNoUpload();
  }

  @ParameterizedTest
  @ValueSource(strings = {"agent", "standaloneMcpState", "host", "detectionSources"})
  void should_drop_missing_agent_fields_after_deserialization(String field) {
    var input = JsonParser.parseString(AGENT).getAsJsonObject();
    input.remove(field);
    service.aiAgentIntegrationStateObserved(RPC_GSON.fromJson(input, AiAgentIntegrationStateObservedParams.class));
    if (!"detectionSources".equals(field)) {
      input.addProperty(field, "NOT_AN_ENUM_VALUE");
      service.aiAgentIntegrationStateObserved(RPC_GSON.fromJson(input, AiAgentIntegrationStateObservedParams.class));
    }
    verifyNoUpload();
  }

  @ParameterizedTest
  @ValueSource(strings = {"[]", "null", "[null]", "[\"IDE\",null]", "[\"INVALID\"]"})
  void should_drop_invalid_sources(String sources) {
    var input = JsonParser.parseString(AGENT).getAsJsonObject();
    input.add("detectionSources", JsonParser.parseString(sources));
    service.aiAgentIntegrationStateObserved(RPC_GSON.fromJson(input, AiAgentIntegrationStateObservedParams.class));
    verifyNoUpload();
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void should_gate_every_event_on_gessie_capability_and_current_consent(boolean capable, boolean consent) {
    service = newService(Set.of(capable ? BackendCapability.GESSIE_TELEMETRY : BackendCapability.TELEMETRY));
    when(setting.isTelemetryEnabledByUser()).thenReturn(consent);
    reportAll();
    verify(http, times(capable && consent ? 4 : 0)).postAsync(anyString(), anyString(), anyString());
  }

  @Test
  void should_use_live_consent_and_emit_repeated_independent_observations() {
    reportAll();
    when(setting.isTelemetryEnabledByUser()).thenReturn(false);
    reportAll();
    when(setting.isTelemetryEnabledByUser()).thenReturn(true);
    reportAll();
    verify(http, times(8)).postAsync(anyString(), anyString(), anyString());
  }

  @Test
  void should_drop_null_reports() {
    service.aiIntegrationAction(null);
    service.aiIntegrationCliStateObserved(null);
    service.aiAgentIntegrationStateObserved(null);
    verifyNoUpload();
  }

  @Test
  void should_isolate_synchronous_submission_and_serialization_failures() {
    when(http.postAsync(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("private response"));
    assertThatCode(this::reportAll).doesNotThrowAnyException();
    when(machineId.getMachineId()).thenThrow(new IllegalStateException("private storage"));
    assertThatCode(this::reportAll).doesNotThrowAnyException();
  }

  @Test
  void should_isolate_asynchronous_submission_failure() {
    when(http.postAsync(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("failed")));
    assertThatCode(this::reportAll).doesNotThrowAnyException();
  }

  private GessieService newService(Set<BackendCapability> capabilities) {
    var params = mock(InitializeParams.class);
    when(params.getBackendCapabilities()).thenReturn(capabilities);
    when(params.getTelemetryConstantAttributes()).thenReturn(new TelemetryClientConstantAttributesDto("vscode", "name", "version", "ide-version", null));
    var provider = mock(HttpClientProvider.class);
    when(provider.getHttpClientWithXApiKeyAndRetries("value")).thenReturn(http);
    return new GessieService(params, new GessieHttpClient(provider, "http://localhost", "value", machineId, storage), setting);
  }

  private void reportAll() {
    service.onStartup();
    service.aiIntegrationAction(RPC_GSON.fromJson(ACTION, AiIntegrationActionParams.class));
    service.aiIntegrationCliStateObserved(RPC_GSON.fromJson(CLI, AiIntegrationCliStateObservedParams.class));
    service.aiAgentIntegrationStateObserved(RPC_GSON.fromJson(AGENT, AiAgentIntegrationStateObservedParams.class));
  }

  private JsonObject uploadedEvent() {
    var body = ArgumentCaptor.forClass(String.class);
    verify(http).postAsync(eq("http://localhost/ide"), eq(HttpClient.JSON_CONTENT_TYPE), body.capture());
    return JsonParser.parseString(body.getValue()).getAsJsonObject();
  }

  private void verifyNoUpload() {
    verify(http, never()).postAsync(anyString(), anyString(), anyString());
  }

  private static void assertMetadata(JsonObject event, String type) {
    assertThat(event.keySet()).containsExactlyInAnyOrder("metadata", "event_payload");
    var metadata = event.getAsJsonObject("metadata");
    assertThat(metadata.keySet()).containsExactlyInAnyOrder("event_id", "source", "event_type", "event_timestamp", "event_version");
    assertThat(UUID.fromString(metadata.get("event_id").getAsString())).isNotNull();
    assertThat(metadata.get("event_type").getAsString()).isEqualTo("Analytics.Editor." + type);
    assertThat(metadata.get("event_version").getAsString()).isEqualTo("1");
    assertThat(metadata.get("event_timestamp").getAsJsonPrimitive().isString()).isTrue();
    assertThat(Long.parseLong(metadata.get("event_timestamp").getAsString())).isPositive();
    assertThat(metadata.get("source")).isEqualTo(JsonParser.parseString("{\"domain\":\"VSCode\"}"));
  }
}
