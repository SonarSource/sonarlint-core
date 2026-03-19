/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.telemetry;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.monitoring.MonitoringUserIdStore;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.telemetry.gessie.GessieConnectionInfo;
import org.sonarsource.sonarlint.core.telemetry.gessie.GessieConnectionInfo.ConnectionType;
import org.sonarsource.sonarlint.core.telemetry.gessie.GessieEventPublisher;
import org.sonarsource.sonarlint.core.telemetry.gessie.GessieService;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.IDESupportedLanguageViewedPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GessieEventPublisherTests {

  private GessieService gessieService;
  private MonitoringUserIdStore monitoringUserIdStore;
  private TelemetryServerAttributesProvider telemetryServerAttributesProvider;
  
  private GessieEventPublisher underTest;

  @BeforeEach
  void setUp() {
    gessieService = mock(GessieService.class);
    monitoringUserIdStore = mock(MonitoringUserIdStore.class);
    telemetryServerAttributesProvider = mock(TelemetryServerAttributesProvider.class);

    var telemetryConstantAttributes = mock(TelemetryClientConstantAttributesDto.class);
    when(telemetryConstantAttributes.getProductKey()).thenReturn("vscode");
    when(telemetryConstantAttributes.getProductVersion()).thenReturn("1.0.0");

    var init = mock(InitializeParams.class);
    when(init.getTelemetryConstantAttributes()).thenReturn(telemetryConstantAttributes);

    underTest = new GessieEventPublisher(gessieService, monitoringUserIdStore, telemetryServerAttributesProvider, init);
  }

  @Test
  void supportedLanguageViewed_does_nothing_when_gessie_disabled() {
    when(gessieService.isEnabled()).thenReturn(false);

    underTest.supportedLanguageViewed("scopeId");

    verify(gessieService, never()).supportedLanguageViewed(any());
  }

  @Test
  void supportedLanguageViewed_does_nothing_when_local_user_id_unavailable() {
    when(gessieService.isEnabled()).thenReturn(true);
    when(monitoringUserIdStore.getOrCreate()).thenReturn(Optional.empty());

    underTest.supportedLanguageViewed("scopeId");

    verify(gessieService, never()).supportedLanguageViewed(any());
  }

  @Test
  void supportedLanguageViewed_sends_event_without_connection_info_for_unbound_scope() {
    when(gessieService.isEnabled()).thenReturn(true);
    when(monitoringUserIdStore.getOrCreate()).thenReturn(Optional.of(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")));
    when(telemetryServerAttributesProvider.getGessieConnectionInfo("scopeId")).thenReturn(Optional.empty());

    underTest.supportedLanguageViewed("scopeId");

    var captor = ArgumentCaptor.forClass(IDESupportedLanguageViewedPayload.class);
    verify(gessieService).supportedLanguageViewed(captor.capture());
    var payload = captor.getValue();
    assertThat(payload.localUserId()).isEqualTo("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    assertThat(payload.connectionType()).isNull();
    assertThat(payload.userUuid()).isNull();
    assertThat(payload.organizationUuidV4()).isNull();
    assertThat(payload.sqsInstallationId()).isNull();
  }

  @Test
  void supportedLanguageViewed_sends_event_with_sqc_connection_info() {
    when(gessieService.isEnabled()).thenReturn(true);
    when(monitoringUserIdStore.getOrCreate()).thenReturn(Optional.of(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")));
    var connectionInfo = new GessieConnectionInfo(ConnectionType.SQC, "user-uuid", "org-uuid-v4", null);
    when(telemetryServerAttributesProvider.getGessieConnectionInfo("scopeId")).thenReturn(Optional.of(connectionInfo));

    underTest.supportedLanguageViewed("scopeId");

    var captor = ArgumentCaptor.forClass(IDESupportedLanguageViewedPayload.class);
    verify(gessieService).supportedLanguageViewed(captor.capture());
    var payload = captor.getValue();
    assertThat(payload.localUserId()).isEqualTo("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    assertThat(payload.connectionType()).isEqualTo(ConnectionType.SQC);
    assertThat(payload.userUuid()).isEqualTo("user-uuid");
    assertThat(payload.organizationUuidV4()).isEqualTo("org-uuid-v4");
    assertThat(payload.sqsInstallationId()).isNull();
  }

  @Test
  void supportedLanguageViewed_sends_event_with_sqs_connection_info() {
    when(gessieService.isEnabled()).thenReturn(true);
    when(monitoringUserIdStore.getOrCreate()).thenReturn(Optional.of(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")));
    var connectionInfo = new GessieConnectionInfo(ConnectionType.SQS, "sqs-user-uuid", null, "installation-id");
    when(telemetryServerAttributesProvider.getGessieConnectionInfo("scopeId")).thenReturn(Optional.of(connectionInfo));

    underTest.supportedLanguageViewed("scopeId");

    var captor = ArgumentCaptor.forClass(IDESupportedLanguageViewedPayload.class);
    verify(gessieService).supportedLanguageViewed(captor.capture());
    var payload = captor.getValue();
    assertThat(payload.localUserId()).isEqualTo("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    assertThat(payload.connectionType()).isEqualTo(ConnectionType.SQS);
    assertThat(payload.userUuid()).isEqualTo("sqs-user-uuid");
    assertThat(payload.organizationUuidV4()).isNull();
    assertThat(payload.sqsInstallationId()).isEqualTo("installation-id");
  }

}
