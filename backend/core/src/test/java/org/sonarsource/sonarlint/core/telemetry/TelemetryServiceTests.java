/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.telemetry;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.TelemetryUpdatedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelemetryServiceTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private final SonarLintRpcClient client = mock(SonarLintRpcClient.class);
  private final TelemetryHttpClient legacyHttp = mock(TelemetryHttpClient.class);
  private final TelemetryServerAttributesProvider attributesProvider = mock(TelemetryServerAttributesProvider.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private TelemetryLocalStorageManager storage;
  private TelemetryManager manager;
  private TelemetryService service;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    storage = new TelemetryLocalStorageManager(temp.resolve("telemetry"), mock(InitializeParams.class));
    manager = new TelemetryManager(storage, legacyHttp);
    when(client.getTelemetryLiveAttributes()).thenReturn(CompletableFuture.completedFuture(new TelemetryClientLiveAttributesResponse(Map.of())));
  }

  @AfterEach
  void stop() {
    if (service != null) {
      service.close();
    }
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void status_and_enable_should_recognize_either_capability(boolean legacy, boolean gessie) {
    var capabilities = new HashSet<BackendCapability>();
    if (legacy) {
      capabilities.add(BackendCapability.TELEMETRY);
    }
    if (gessie) {
      capabilities.add(BackendCapability.GESSIE_TELEMETRY);
    }
    manager.setTelemetryEnabledByUser(false);
    start(capabilities);
    service.enableTelemetry();

    assertThat(service.getStatus().isEnabled()).isEqualTo(legacy || gessie);
    assertThat(storage.isEnabled()).isEqualTo(legacy || gessie);
    assertThat(service.isEnabled()).isEqualTo(legacy);
    if (!legacy) {
      verifyNoInteractions(attributesProvider, client, legacyHttp);
    }
  }

  @Test
  void disable_should_persist_even_without_capabilities() {
    start(Set.of());
    service.disableTelemetry();
    assertThat(storage.isEnabled()).isFalse();
    verify(events).publishEvent(new TelemetryUpdatedEvent(false));
    verifyNoInteractions(attributesProvider, client, legacyHttp);
  }

  @ParameterizedTest
  @CsvSource({"false", "true"})
  void consent_and_notifications_should_not_depend_on_attribute_fetch_success(boolean enable) {
    start(Set.of(BackendCapability.TELEMETRY));
    manager.setTelemetryEnabledByUser(!enable);
    when(client.getTelemetryLiveAttributes()).thenAnswer(invocation -> {
      assertThat(storage.isEnabled()).isEqualTo(enable);
      verify(events).publishEvent(new TelemetryUpdatedEvent(enable));
      return CompletableFuture.failedFuture(new IllegalStateException("unavailable"));
    });

    changeConsent(enable);

    assertThat(storage.isEnabled()).isEqualTo(enable);
    verifyNoInteractions(legacyHttp);
  }

  @ParameterizedTest
  @CsvSource({"false", "true"})
  void transport_failure_should_not_roll_back_consent(boolean enable) {
    start(Set.of(BackendCapability.TELEMETRY));
    manager.setTelemetryEnabledByUser(!enable);
    if (enable) {
      doThrow(new IllegalStateException("transport")).when(legacyHttp).upload(any(), any());
    } else {
      doThrow(new IllegalStateException("transport")).when(legacyHttp).optOut(any(), any());
    }

    changeConsent(enable);

    assertThat(storage.isEnabled()).isEqualTo(enable);
    verify(events).publishEvent(new TelemetryUpdatedEvent(enable));
  }

  private void changeConsent(boolean enable) {
    if (enable) {
      service.enableTelemetry();
    } else {
      service.disableTelemetry();
    }
  }

  private void start(Set<BackendCapability> capabilities) {
    var params = mock(InitializeParams.class);
    when(params.getBackendCapabilities()).thenReturn(capabilities);
    service = new TelemetryService(params, client, attributesProvider, manager, events);
  }
}
