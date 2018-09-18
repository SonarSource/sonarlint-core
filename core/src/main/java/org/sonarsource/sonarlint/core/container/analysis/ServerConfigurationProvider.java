/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import org.sonar.api.config.Configuration;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.internal.MapSettings;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;

/**
 * Can't put {@link ServerConfiguration} directly in pico since it would conflict with {@link MutableAnalysisSettings}.
 *
 */
public class ServerConfigurationProvider {

  private Configuration serverConfig;

  public ServerConfigurationProvider(StorageReader storage, StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
    this.serverConfig = new ServerConfiguration(storage, config, propertyDefinitions).asConfig();
  }

  public ServerConfigurationProvider(StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
    this.serverConfig = new ServerConfiguration(null, config, propertyDefinitions).asConfig();
  }

  public static class ServerConfiguration extends MapSettings {

    private final Map<String, String> properties = new HashMap<>();

    private ServerConfiguration(@Nullable StorageReader storage, StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
      super(propertyDefinitions);
      if (storage != null) {
        GlobalProperties globalProps = storage.readGlobalProperties();
        addProperties(globalProps.getPropertiesMap());
        if (config instanceof ConnectedAnalysisConfiguration && ((ConnectedAnalysisConfiguration) config).projectKey() != null) {
          Sonarlint.ProjectConfiguration projectConfig = storage.readProjectConfig(((ConnectedAnalysisConfiguration) config).projectKey());
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

  public Configuration getServerConfig() {
    return serverConfig;
  }

}
