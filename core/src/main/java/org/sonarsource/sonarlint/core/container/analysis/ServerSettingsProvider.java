/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.api.config.Encryption;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;

/**
 * Can't put ServerSettings directly in pico since it would conflict with {@link AnalysisSettings}.
 *
 */
public class ServerSettingsProvider {

  private ServerSettings settings;

  public ServerSettingsProvider(StorageReader storage, StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
    this.settings = new ServerSettings(storage, config, propertyDefinitions);
  }

  public ServerSettingsProvider(StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
    this.settings = new ServerSettings(null, config, propertyDefinitions);
  }

  public static class ServerSettings extends Settings {

    private final Map<String, String> properties = new HashMap<>();

    private ServerSettings(@Nullable StorageReader storage, StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
      super(propertyDefinitions, new Encryption(null));
      if (storage != null) {
        GlobalProperties globalProps = storage.readGlobalProperties();
        addProperties(globalProps.getPropertiesMap());
        if (config instanceof ConnectedAnalysisConfiguration && ((ConnectedAnalysisConfiguration) config).moduleKey() != null) {
          ModuleConfiguration projectConfig = storage.readModuleConfig(((ConnectedAnalysisConfiguration) config).moduleKey());
          addProperties(projectConfig.getPropertiesMap());
        }
      }
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

  public ServerSettings getServerSettings() {
    return settings;
  }

}
