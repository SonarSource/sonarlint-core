/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisExtraPropertiesServiceTests {

  private static final String CONFIG_SCOPE_ID = "scopeId";
  AnalysisExtraPropertiesService underTest;

  @BeforeEach
  public void setup() {
    var initializeParams = mock(InitializeParams.class);
    when(initializeParams.getExtraAnalyserPropsByConfigScopeId()).thenReturn(new HashMap<>());
    underTest = new AnalysisExtraPropertiesService(initializeParams);
  }

  @Test
  void it_should_override_only_provided_properties() {
    var extraProperties = underTest.getExtraProperties(CONFIG_SCOPE_ID);
    assertThat(extraProperties).isEmpty();

    underTest.setOrUpdateExtraProperties(CONFIG_SCOPE_ID, Map.of("key1", "value1", "key2", "value2"));
    extraProperties = underTest.getExtraProperties(CONFIG_SCOPE_ID);
    assertThat(extraProperties).hasSize(2).containsEntry("key1", "value1").containsEntry("key2", "value2");

    underTest.setOrUpdateExtraProperties(CONFIG_SCOPE_ID, Map.of("key2", "new-value2", "key3", "new-value3"));

    extraProperties = underTest.getExtraProperties(CONFIG_SCOPE_ID);
    assertThat(extraProperties).hasSize(3).containsEntry("key1", "value1").containsEntry("key2", "new-value2").containsEntry("key3", "new-value3");
  }

  @Test
  void it_should_remove_previous_config_and_set_provided_properties() {
    var extraProperties = underTest.getExtraProperties(CONFIG_SCOPE_ID);
    assertThat(extraProperties).isEmpty();

    underTest.setOrUpdateExtraProperties(CONFIG_SCOPE_ID, Map.of("key1", "value1", "key2", "value2"));
    extraProperties = underTest.getExtraProperties(CONFIG_SCOPE_ID);
    assertThat(extraProperties).hasSize(2).containsEntry("key1", "value1").containsEntry("key2", "value2");

    underTest.setExtraProperties(CONFIG_SCOPE_ID, Map.of("key2", "new-value2", "key3", "new-value3"));

    extraProperties = underTest.getExtraProperties(CONFIG_SCOPE_ID);
    assertThat(extraProperties).hasSize(2).containsEntry("key2", "new-value2").containsEntry("key3", "new-value3");
  }

}
