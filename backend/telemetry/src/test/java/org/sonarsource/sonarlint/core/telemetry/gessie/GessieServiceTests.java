/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.gessie;

import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.telemetry.common.TelemetryUserSetting;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieEvent;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.IDESupportedLanguageViewedPayload;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.MessagePayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GessieServiceTests {

  private GessieHttpClient gessieHttpClient;
  private TelemetryUserSetting telemetryUserSetting;
  private InitializeParams initParams;

  @BeforeEach
  void setUp() {
    gessieHttpClient = mock(GessieHttpClient.class);
    telemetryUserSetting = mock(TelemetryUserSetting.class);

    var telemetryConstantAttributes = mock(TelemetryClientConstantAttributesDto.class);
    when(telemetryConstantAttributes.getProductKey()).thenReturn("vscode");
    when(telemetryConstantAttributes.getProductVersion()).thenReturn("1.0.0");

    initParams = mock(InitializeParams.class);
    when(initParams.getTelemetryConstantAttributes()).thenReturn(telemetryConstantAttributes);
  }

  @Test
  void isEnabled_returns_true_when_feature_flag_and_user_consent_are_both_set() {
    when(initParams.getBackendCapabilities()).thenReturn(EnumSet.of(BackendCapability.GESSIE_TELEMETRY));
    when(telemetryUserSetting.isTelemetryEnabledByUser()).thenReturn(true);

    var underTest = new GessieService(initParams, gessieHttpClient, telemetryUserSetting);

    assertThat(underTest.isEnabled()).isTrue();
  }

  @Test
  void isEnabled_returns_false_when_feature_flag_is_not_set() {
    when(initParams.getBackendCapabilities()).thenReturn(EnumSet.noneOf(BackendCapability.class));
    when(telemetryUserSetting.isTelemetryEnabledByUser()).thenReturn(true);

    var underTest = new GessieService(initParams, gessieHttpClient, telemetryUserSetting);

    assertThat(underTest.isEnabled()).isFalse();
  }

  @Test
  void isEnabled_returns_false_when_user_has_not_consented() {
    when(initParams.getBackendCapabilities()).thenReturn(EnumSet.of(BackendCapability.GESSIE_TELEMETRY));
    when(telemetryUserSetting.isTelemetryEnabledByUser()).thenReturn(false);

    var underTest = new GessieService(initParams, gessieHttpClient, telemetryUserSetting);

    assertThat(underTest.isEnabled()).isFalse();
  }

  @Test
  void onStartup_posts_plugin_activated_event_when_enabled() {
    when(initParams.getBackendCapabilities()).thenReturn(EnumSet.of(BackendCapability.GESSIE_TELEMETRY));
    when(telemetryUserSetting.isTelemetryEnabledByUser()).thenReturn(true);

    var underTest = new GessieService(initParams, gessieHttpClient, telemetryUserSetting);
    underTest.onStartup();

    ArgumentCaptor<GessieEvent> captor = ArgumentCaptor.forClass(GessieEvent.class);
    verify(gessieHttpClient).postEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.metadata().eventType()).isEqualTo(MessagePayload.EVENT_TYPE);
    assertThat(event.metadata().eventVersion()).isEqualTo(MessagePayload.EVENT_VERSION);
    assertThat(event.eventPayload()).isInstanceOf(MessagePayload.class);
    var payload = (MessagePayload) event.eventPayload();
    assertThat(payload.message()).isEqualTo("Gessie integration test event");
    assertThat(payload.trigger()).isEqualTo("slcore_start");
  }

  @Test
  void onStartup_does_not_post_event_when_disabled() {
    when(initParams.getBackendCapabilities()).thenReturn(EnumSet.noneOf(BackendCapability.class));
    when(telemetryUserSetting.isTelemetryEnabledByUser()).thenReturn(true);

    var underTest = new GessieService(initParams, gessieHttpClient, telemetryUserSetting);
    underTest.onStartup();

    verify(gessieHttpClient, never()).postEvent(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void supportedLanguageViewed_posts_correct_event() {
    when(initParams.getBackendCapabilities()).thenReturn(EnumSet.of(BackendCapability.GESSIE_TELEMETRY));
    when(telemetryUserSetting.isTelemetryEnabledByUser()).thenReturn(true);

    var underTest = new GessieService(initParams, gessieHttpClient, telemetryUserSetting);

    var payload = new IDESupportedLanguageViewedPayload(
      "local-user-id",
      "1.2.3",
      "Linux",
      IDESupportedLanguageViewedPayload.ConnectionType.SQC,
      "user-uuid",
      "org-uuid-v4",
      null
    );
    underTest.supportedLanguageViewed(payload);

    ArgumentCaptor<GessieEvent> captor = ArgumentCaptor.forClass(GessieEvent.class);
    verify(gessieHttpClient).postEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.metadata().eventType()).isEqualTo(IDESupportedLanguageViewedPayload.EVENT_TYPE);
    assertThat(event.metadata().eventVersion()).isEqualTo(IDESupportedLanguageViewedPayload.EVENT_VERSION);
    assertThat(event.eventPayload()).isEqualTo(payload);
  }

}
