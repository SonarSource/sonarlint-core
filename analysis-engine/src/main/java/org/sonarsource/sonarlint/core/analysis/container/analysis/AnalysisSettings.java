/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.util.HashMap;
import java.util.Map;
import org.sonar.api.config.PropertyDefinitions;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalSettings;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MapSettings;

public class AnalysisSettings extends MapSettings {

  public AnalysisSettings(GlobalSettings globalSettings, AnalysisConfiguration analysisConfig, PropertyDefinitions propertyDefinitions) {
    super(propertyDefinitions, mergeInOrder(globalSettings, analysisConfig));
  }

  private static Map<String, String> mergeInOrder(GlobalSettings globalSettings, AnalysisConfiguration analysisConfig) {
    Map<String, String> result = new HashMap<>(globalSettings.getProperties());
    result.putAll(analysisConfig.extraProperties());
    return result;
  }

}
