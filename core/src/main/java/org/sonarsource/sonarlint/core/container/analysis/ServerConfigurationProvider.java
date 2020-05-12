/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.container.standalone.rule.EmptyConfiguration;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.trim;

/**
 * Can't put {@link ServerConfiguration} directly in pico since it would conflict with {@link MutableAnalysisConfiguration}.
 */
public class ServerConfigurationProvider {

  private Configuration serverConfig;

  /**
   * Connected mode
   */
  public ServerConfigurationProvider(StorageReader storage, ConnectedAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
    this.serverConfig = new ServerConfiguration(storage, config, propertyDefinitions);
  }

  /**
   * Standalone mode
   */
  public ServerConfigurationProvider(StandaloneAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
    this.serverConfig = new EmptyConfiguration();
  }

  // For testing
  public ServerConfigurationProvider(Map<String, String> properties) {
    this.serverConfig = new ServerConfiguration(new PropertyDefinitions(System2.INSTANCE), properties);
  }

  public static class ServerConfiguration implements Configuration {

    private final PropertyDefinitions definitions;
    private final Map<String, String> properties = new HashMap<>();

    // For testing
    protected ServerConfiguration(PropertyDefinitions propertyDefinitions, Map<String, String> properties) {
      this.definitions = propertyDefinitions;
      this.properties.putAll(properties);
    }

    private ServerConfiguration(@Nullable StorageReader storage, AbstractAnalysisConfiguration config, PropertyDefinitions propertyDefinitions) {
      this.definitions = propertyDefinitions;
      if (storage != null) {
        GlobalProperties globalProps = storage.readGlobalProperties();
        addProperties(globalProps.getPropertiesMap());
        if (config instanceof ConnectedAnalysisConfiguration && ((ConnectedAnalysisConfiguration) config).projectKey() != null) {
          Sonarlint.ProjectConfiguration projectConfig = storage.readProjectConfig(((ConnectedAnalysisConfiguration) config).projectKey());
          addProperties(projectConfig.getPropertiesMap());
        }
      }
    }

    public void addProperties(Map<String, String> props) {
      for (Map.Entry<String, String> entry : props.entrySet()) {
        setProperty(entry.getKey(), entry.getValue());
      }
    }

    protected ServerConfiguration setProperty(String key, @Nullable String value) {
      String validKey = definitions.validKey(key);
      if (value == null) {
        properties.remove(validKey);
      } else {
        properties.put(validKey, trim(value));
      }
      return this;
    }

    @Override
    public Optional<String> get(String key) {
      return Optional.ofNullable(getString(key));
    }

    private String getString(String key) {
      String effectiveKey = definitions.validKey(key);
      Optional<String> value = getRawString(effectiveKey);
      if (!value.isPresent()) {
        // default values cannot be encrypted, so return value as-is.
        return getDefaultValue(effectiveKey);
      }
      return value.get();
    }

    private Optional<String> getRawString(String key) {
      return Optional.ofNullable(properties.get(definitions.validKey(requireNonNull(key))));
    }

    @CheckForNull
    public String getDefaultValue(String key) {
      return definitions.getDefaultValue(key);
    }

    @Override
    public boolean hasKey(String key) {
      return getRawString(key).isPresent();
    }

    @Override
    public String[] getStringArray(String key) {
      String effectiveKey = definitions.validKey(key);
      Optional<PropertyDefinition> def = getDefinition(effectiveKey);
      if ((def.isPresent()) && (def.get().multiValues())) {
        String value = getString(key);
        if (value == null) {
          return ArrayUtils.EMPTY_STRING_ARRAY;
        }

        return Arrays.stream(value.split(",", -1)).map(String::trim)
          .map(s -> s.replace("%2C", ","))
          .toArray(String[]::new);
      }

      return getStringArrayBySeparator(key, ",");
    }

    protected Optional<PropertyDefinition> getDefinition(String effectiveKey) {
      return Optional.ofNullable(definitions.get(effectiveKey));
    }

    private String[] getStringArrayBySeparator(String key, String separator) {
      String value = getString(key);
      if (value != null) {
        String[] strings = StringUtils.splitByWholeSeparator(value, separator);
        String[] result = new String[strings.length];
        for (int index = 0; index < strings.length; index++) {
          result[index] = trim(strings[index]);
        }
        return result;
      }
      return ArrayUtils.EMPTY_STRING_ARRAY;
    }
  }

  public Configuration getServerConfig() {
    return serverConfig;
  }

}
