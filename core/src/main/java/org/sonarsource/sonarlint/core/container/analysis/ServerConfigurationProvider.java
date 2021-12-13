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

import java.util.Map;
import javax.annotation.Nullable;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.container.global.MapSettings;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.rule.extractor.EmptyConfiguration;
import org.sonarsource.sonarlint.core.storage.ProjectStorage;

/**
 * Can't put {@link ConnectionConfiguration} directly in pico since it would conflict with {@link MutableAnalysisSettings}.
 *
 */
public class ServerConfigurationProvider {

  private Configuration serverConfig;

  /**
   * Connected mode
   */
  public ServerConfigurationProvider(StorageReader storage, ProjectStorage projectStorage, ConnectedAnalysisConfiguration config,
    PropertyDefinitions propertyDefinitions) {
    this.serverConfig = new ConfigurationBridge(new ServerConfiguration(storage, projectStorage, config, propertyDefinitions));
  }

  /**
   * Standalone mode
   */
  public ServerConfigurationProvider(StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
    this.serverConfig = new EmptyConfiguration();
  }

  // For testing
  public ServerConfigurationProvider(Map<String, String> properties) {
    this.serverConfig = new ConfigurationBridge(new ServerConfiguration(properties));
  }

  public static class ServerConfiguration extends MapSettings {

    // For testing
    private ServerConfiguration(Map<String, String> properties) {
      super(new PropertyDefinitions(System2.INSTANCE));
      addProperties(properties);
    }

    private ServerConfiguration(@Nullable StorageReader storage, ProjectStorage projectStorage, AbstractAnalysisConfiguration config,
      PropertyDefinitions propertyDefinitions) {
      super(propertyDefinitions);
      if (storage != null && config instanceof ConnectedAnalysisConfiguration) {
        var projectKey = ((ConnectedAnalysisConfiguration) config).projectKey();
        if (projectKey != null) {
          addProperties(projectStorage.getAnalyzerConfiguration(projectKey).getSettings().getAll());
        }
      }
    }

  }

  public Configuration getServerConfig() {
    return serverConfig;
  }

}
