/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.analysis;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalysisSettingsTest {
  private final PropertyDefinitions propertyDefinitions = new PropertyDefinitions(System2.INSTANCE, Collections.emptyList());
  private final GlobalSettings globalSettings = mock(GlobalSettings.class);

  @Test
  void trimAnalysisPropertyKeys() {
    AnalysisConfiguration analysisConfiguration = AnalysisConfiguration.builder()
      .putAllExtraProperties(Map.of("key1   ", "value1", "key1 ", "value11")).build();

    AnalysisSettings analysisSettings = new AnalysisSettings(globalSettings, analysisConfiguration, propertyDefinitions);

    assertThat(analysisSettings.getProperties().keySet())
      .contains("key1")
      .hasSize(1);
  }

}