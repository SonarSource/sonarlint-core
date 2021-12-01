/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis;

import org.sonar.api.config.PropertyDefinitions;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.container.global.GlobalSettings;
import org.sonarsource.sonarlint.core.container.global.MapSettings;
import org.sonarsource.sonarlint.core.container.storage.GlobalSettingsStore;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;

public class MutableAnalysisSettings extends MapSettings {

  /**
   * Standalone mode
   */
  public MutableAnalysisSettings(GlobalSettings globalSettings, AbstractAnalysisConfiguration analysisConfig, PropertyDefinitions propertyDefinitions) {
    super(propertyDefinitions);
    addPropertiesInOrder(globalSettings, analysisConfig);
  }

  /**
   * Connected mode
   */
  public MutableAnalysisSettings(StorageReader storage, GlobalSettingsStore globalSettingsStore, GlobalSettings globalSettings, AbstractAnalysisConfiguration analysisConfig,
    PropertyDefinitions propertyDefinitions) {
    super(propertyDefinitions);
    addProperties(globalSettingsStore.getAll());
    if (analysisConfig instanceof ConnectedAnalysisConfiguration) {
      String projectKey = ((ConnectedAnalysisConfiguration) analysisConfig).projectKey();
      if (projectKey != null) {
        ProjectConfiguration projectConfig = storage.readProjectConfig(projectKey);
        addProperties(projectConfig.getPropertiesMap());
      }
    }
    addPropertiesInOrder(globalSettings, analysisConfig);
  }

  private void addPropertiesInOrder(GlobalSettings globalSettings, AbstractAnalysisConfiguration analysisConfig) {
    addProperties(globalSettings.getProperties());
    addProperties(analysisConfig.extraProperties());
  }

}
