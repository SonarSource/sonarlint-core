/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.sonar.api.config.Encryption;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;

public class AnalysisSettings extends Settings {

  private final Map<String, String> properties = new HashMap<>();

  public AnalysisSettings(StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
    super(propertyDefinitions, new Encryption(null));
    addProperties(config.extraProperties());
  }

  public AnalysisSettings(StorageManager storage, StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
    super(propertyDefinitions, new Encryption(null));
    GlobalProperties globalProps = storage.readGlobalPropertiesFromStorage();
    addProperties(globalProps.getPropertiesMap());
    if (config instanceof ConnectedAnalysisConfiguration && ((ConnectedAnalysisConfiguration) config).moduleKey() != null) {
      ModuleConfiguration projectConfig = storage.readModuleConfigFromStorage(((ConnectedAnalysisConfiguration) config).moduleKey());
      addProperties(projectConfig.getPropertiesMap());
    }
    addProperties(config.extraProperties());
  }

  @Override
  protected Optional<String> get(String key) {
    return Optional.ofNullable(properties.get(key));
  }

  @Override
  protected void set(String key, String value) {
    properties.put(key, value);
  }

  @Override
  protected void remove(String key) {
    properties.remove(key);
  }

  @Override
  public Map<String, String> getProperties() {
    return properties;
  }
}
