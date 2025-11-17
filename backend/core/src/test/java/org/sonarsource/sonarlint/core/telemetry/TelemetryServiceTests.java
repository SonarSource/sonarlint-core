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

import java.util.EnumSet;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingMode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TelemetryServiceTests {

  private TelemetryManager telemetryManager;
  private TelemetryServerAttributesProvider telemetryServerAttributesProvider;
  private SonarLintRpcClient client;

  private TelemetryService underTest;

  @BeforeEach
  void setUp() {
    telemetryManager = mock(TelemetryManager.class);
    telemetryServerAttributesProvider = mock(TelemetryServerAttributesProvider.class);
    client = mock(SonarLintRpcClient.class);

    var init = mock(InitializeParams.class);
    when(init.getBackendCapabilities()).thenReturn(EnumSet.of(BackendCapability.TELEMETRY));
    when(init.isFocusOnNewCode()).thenReturn(false);

    when(telemetryManager.isTelemetryEnabledByUser()).thenReturn(true);

    underTest = spy(new TelemetryService(init, client, telemetryServerAttributesProvider, telemetryManager));
    doReturn(true).when(underTest).isEnabled();

    clearInvocations(telemetryManager);
  }

  @Test
  void should_increment_manual_counter_for_manual_binding() {
    underTest.addedNewBinding(BindingMode.MANUAL, null);

    ArgumentCaptor<Consumer<TelemetryLocalStorage>> captor = ArgumentCaptor.forClass(Consumer.class);
    verify(telemetryManager).updateTelemetry(captor.capture());
    var storage = mock(TelemetryLocalStorage.class);
    captor.getValue().accept(storage);
    verify(storage).incrementNewBindingsManualCount();
    verifyNoMoreInteractions(storage);
  }

  @Test
  void should_increment_remote_url_counter_for_assisted_remote_url() {
    underTest.addedNewBinding(BindingMode.FROM_SUGGESTION, BindingSuggestionOrigin.REMOTE_URL);

    ArgumentCaptor<Consumer<TelemetryLocalStorage>> captor = ArgumentCaptor.forClass(Consumer.class);
    verify(telemetryManager).updateTelemetry(captor.capture());
    var storage = mock(TelemetryLocalStorage.class);
    captor.getValue().accept(storage);
    verify(storage).incrementNewBindingsRemoteUrlCount();
    verifyNoMoreInteractions(storage);
  }

  @Test
  void should_increment_project_name_counter_for_assisted_project_name() {
    underTest.addedNewBinding(BindingMode.FROM_SUGGESTION, BindingSuggestionOrigin.PROJECT_NAME);

    ArgumentCaptor<Consumer<TelemetryLocalStorage>> captor = ArgumentCaptor.forClass(Consumer.class);
    verify(telemetryManager).updateTelemetry(captor.capture());
    var storage = mock(TelemetryLocalStorage.class);
    captor.getValue().accept(storage);
    verify(storage).incrementNewBindingsProjectNameCount();
    verifyNoMoreInteractions(storage);
  }

  @Test
  void should_increment_shared_config_counter_for_assisted_shared_config() {
    underTest.addedNewBinding(BindingMode.FROM_SUGGESTION, BindingSuggestionOrigin.SHARED_CONFIGURATION);

    ArgumentCaptor<Consumer<TelemetryLocalStorage>> captor = ArgumentCaptor.forClass(Consumer.class);
    verify(telemetryManager).updateTelemetry(captor.capture());
    var storage = mock(TelemetryLocalStorage.class);
    captor.getValue().accept(storage);
    verify(storage).incrementNewBindingsSharedConfigurationCount();
    verifyNoMoreInteractions(storage);
  }

  @Test
  void should_increment_properties_file_counter_for_assisted_properties_file() {
    underTest.addedNewBinding(BindingMode.FROM_SUGGESTION, BindingSuggestionOrigin.PROPERTIES_FILE);

    ArgumentCaptor<Consumer<TelemetryLocalStorage>> captor = ArgumentCaptor.forClass(Consumer.class);
    verify(telemetryManager).updateTelemetry(captor.capture());
    var storage = mock(TelemetryLocalStorage.class);
    captor.getValue().accept(storage);
    verify(storage).incrementNewBindingsPropertiesFileCount();
    verifyNoMoreInteractions(storage);
  }

  @Test
  void should_do_nothing_when_assisted_and_origin_null() {
    underTest.addedNewBinding(BindingMode.FROM_SUGGESTION, null);
    verify(telemetryManager, never()).updateTelemetry(any());
  }
}
