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

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiAgentIntegrationStateObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationCliStateObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationFailureCategory;
import org.sonarsource.sonarlint.core.telemetry.common.TelemetryUserSetting;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GessieServiceTests {
  private final GessieHttpClient client = mock(GessieHttpClient.class);
  private final TelemetryUserSetting setting = mock(TelemetryUserSetting.class);
  private GessieService service;

  @BeforeEach
  void setUp() {
    when(setting.isTelemetryEnabledByUser()).thenReturn(true);
    service = newService(true);
  }

  @Test
  void should_default_missing_failure_category_and_keep_agent() {
    service.aiIntegrationAction(action(AiIntegrationActionStatus.FAILED, null));

    var event = sentEvent();
    assertThat(event.metadata().eventType()).isEqualTo("Analytics.Editor.IdeAiIntegrationAction");
    var payload = (AiIntegrationActionParams) event.eventPayload();
    assertThat(payload.getFailureCategory()).isEqualTo(AiIntegrationFailureCategory.UNKNOWN);
    assertThat(payload.getAgent()).isEqualTo(AiAgent.CODEX);
  }

  @Test
  void should_preserve_failed_category() {
    service.aiIntegrationAction(action(AiIntegrationActionStatus.FAILED, AiIntegrationFailureCategory.UNSUPPORTED));

    var payload = (AiIntegrationActionParams) sentEvent().eventPayload();
    assertThat(payload.getFailureCategory()).isEqualTo(AiIntegrationFailureCategory.UNSUPPORTED);
  }

  @ParameterizedTest
  @EnumSource(value = AiIntegrationActionStatus.class, names = {"STARTED", "SUCCEEDED", "CANCELLED", "UNKNOWN"})
  void should_clear_failure_category_for_nonfailed_actions(AiIntegrationActionStatus status) {
    service.aiIntegrationAction(action(status, AiIntegrationFailureCategory.TERMINAL_ERROR));

    var payload = (AiIntegrationActionParams) sentEvent().eventPayload();
    assertThat(payload.getFailureCategory()).isNull();
  }

  @Test
  void should_order_and_deduplicate_agent_sources() {
    service.aiAgentIntegrationStateObserved(new AiAgentIntegrationStateObservedParams(
      AiAgent.CURSOR, List.of(AiAgentDetectionSource.CLI, AiAgentDetectionSource.IDE, AiAgentDetectionSource.CLI),
      McpConfigurationState.UNKNOWN, AiIntegrationHost.VSCODE));

    var event = sentEvent();
    assertThat(event.metadata().eventType()).isEqualTo("Analytics.Editor.IdeAiAgentIntegrationStateObserved");
    var payload = (AiAgentIntegrationStateObservedParams) event.eventPayload();
    assertThat(payload.getDetectionSources()).containsExactly(AiAgentDetectionSource.IDE, AiAgentDetectionSource.CLI);
  }

  @Test
  void should_drop_incomplete_reports() {
    service.aiIntegrationAction(null);
    service.aiIntegrationCliStateObserved(new AiIntegrationCliStateObservedParams(null, CliAuthenticationStatus.UNKNOWN, AiIntegrationHost.VSCODE));
    service.aiAgentIntegrationStateObserved(new AiAgentIntegrationStateObservedParams(
      AiAgent.CURSOR, List.of(), McpConfigurationState.UNKNOWN, AiIntegrationHost.VSCODE));

    verifyNoInteractions(client);
  }

  @Test
  void should_gate_on_capability_and_current_consent() {
    service = newService(false);
    service.onStartup();
    service.aiIntegrationAction(action(AiIntegrationActionStatus.FAILED, null));
    verifyNoInteractions(client);

    service = newService(true);
    when(setting.isTelemetryEnabledByUser()).thenReturn(false);
    service.aiIntegrationAction(action(AiIntegrationActionStatus.FAILED, null));
    verifyNoInteractions(client);

    when(setting.isTelemetryEnabledByUser()).thenReturn(true);
    service.aiIntegrationAction(action(AiIntegrationActionStatus.FAILED, null));
    verify(client).postEvent(any(GessieEvent.class));
  }

  @Test
  void should_isolate_synchronous_submission_failure() {
    doThrow(new IllegalStateException("private response")).when(client).postEvent(any(GessieEvent.class));

    assertThatCode(() -> service.aiIntegrationAction(action(AiIntegrationActionStatus.FAILED, null))).doesNotThrowAnyException();
  }

  private GessieService newService(boolean capable) {
    var params = mock(InitializeParams.class);
    when(params.getBackendCapabilities()).thenReturn(Set.of(capable ? BackendCapability.GESSIE_TELEMETRY : BackendCapability.TELEMETRY));
    when(params.getTelemetryConstantAttributes()).thenReturn(new TelemetryClientConstantAttributesDto("vscode", "name", "version", "ide-version", null));
    return new GessieService(params, client, setting);
  }

  private static AiIntegrationActionParams action(AiIntegrationActionStatus status, AiIntegrationFailureCategory failureCategory) {
    return new AiIntegrationActionParams(AiIntegrationAction.INSTALL_CLI, status, failureCategory, AiAgent.CODEX, AiIntegrationHost.VSCODE);
  }

  private GessieEvent sentEvent() {
    var captor = ArgumentCaptor.forClass(GessieEvent.class);
    verify(client).postEvent(captor.capture());
    return captor.getValue();
  }
}
