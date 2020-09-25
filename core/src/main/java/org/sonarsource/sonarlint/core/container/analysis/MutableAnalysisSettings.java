/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;

public class MutableAnalysisSettings extends MapSettings {
  private static final String C_SUFFIXES_KEY = "sonar.c.file.suffixes";
  private static final String CPP_SUFFIXES_KEY = "sonar.cpp.file.suffixes";
  private static final String OBJC_SUFFIXES_KEY = "sonar.objc.file.suffixes";
  private static final String DISABLED_SUFFIX = "disabled";

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
  public MutableAnalysisSettings(StorageReader storage, GlobalSettings globalSettings, AbstractAnalysisConfiguration analysisConfig,
    PropertyDefinitions propertyDefinitions) {
    super(propertyDefinitions);
    GlobalProperties globalProps = storage.readGlobalProperties();
    addProperties(globalProps.getPropertiesMap());
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
    addDefaultProperties(analysisConfig);
    addProperties(analysisConfig.extraProperties());
  }

  private void addDefaultProperties(AbstractAnalysisConfiguration config) {
    if (!config.extraProperties().containsKey("sonar.cfamily.build-wrapper-output")) {
      setProperty(C_SUFFIXES_KEY, DISABLED_SUFFIX);
      setProperty(CPP_SUFFIXES_KEY, DISABLED_SUFFIX);
      setProperty(OBJC_SUFFIXES_KEY, DISABLED_SUFFIX);
    }
  }

}
