/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.newcode;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionResponse;
import org.sonarsource.sonarlint.core.serverconnection.repository.NewCodeDefinitionRepository;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewCodeServiceTests {

  private ConfigurationRepository mockConfigRepository;
  private NewCodeDefinitionRepository mockNewCodeDefinitionRepository;

  private NewCodeService underTest;

  @BeforeEach
  void setup() {
    mockConfigRepository = mock(ConfigurationRepository.class);
    mockNewCodeDefinitionRepository = mock(NewCodeDefinitionRepository.class);
    underTest = new NewCodeService(mockConfigRepository, mockNewCodeDefinitionRepository, mock(TelemetryService.class));
  }

  @Test
  void getNewCodeDefinition_noBinding() {
    var ncd = underTest.getNewCodeDefinition("scope");
    assertThat(ncd).extracting(GetNewCodeDefinitionResponse::getDescription, GetNewCodeDefinitionResponse::isSupported)
      .containsExactly("From last 30 days", true);
  }

  @Test
  void getNewCodeDefinition_noNcdSynchronized() {
    String scopeId = "scope";
    var connectionId = "connectionId";
    var projectKey = "projectKey";
    var effectiveBinding = new Binding(connectionId, projectKey);
    when(mockConfigRepository.getEffectiveBinding(scopeId))
      .thenReturn(Optional.of(effectiveBinding));
    when(mockNewCodeDefinitionRepository.read(connectionId, projectKey))
      .thenReturn(Optional.empty());
    var ncd = underTest.getNewCodeDefinition("scope");
    assertThat(ncd).extracting(GetNewCodeDefinitionResponse::getDescription, GetNewCodeDefinitionResponse::isSupported)
      .containsExactly("No new code definition found", false);
  }

  @Test
  void getNewCodeDefinition_readFromStorage() {
    String scopeId = "scope";
    var connectionId = "connectionId";
    var projectKey = "projectKey";
    var effectiveBinding = new Binding(connectionId, projectKey);
    when(mockConfigRepository.getEffectiveBinding(scopeId))
      .thenReturn(Optional.of(effectiveBinding));
    var newCodeDefinition = NewCodeDefinition.withNumberOfDaysWithDate(42, 1234567890123L);
    when(mockNewCodeDefinitionRepository.read(connectionId, projectKey))
      .thenReturn(Optional.of(newCodeDefinition));
    var ncd = underTest.getNewCodeDefinition("scope");
    assertThat(ncd).extracting(GetNewCodeDefinitionResponse::getDescription, GetNewCodeDefinitionResponse::isSupported)
      .containsExactly("From last 42 days", true);
  }
}
