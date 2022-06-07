/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;

/**
 * To use SonarLint in connected mode please provide a connection id that will identify the storage.
 */
public class ConnectedGlobalConfiguration extends AbstractGlobalConfiguration {

  public static final String DEFAULT_STORAGE_DIR = "storage";

  private final String connectionId;
  private final Path storageRoot;
  private final Map<String, Path> overriddenPluginsPathsByKey;
  private final Map<String, Path> extraPluginsPathsByKey;
  private final boolean isSonarCloud;

  private ConnectedGlobalConfiguration(Builder builder) {
    super(builder);
    this.connectionId = builder.connectionId;
    this.storageRoot = builder.storageRoot != null ? builder.storageRoot : getSonarLintUserHome().resolve(DEFAULT_STORAGE_DIR);
    this.overriddenPluginsPathsByKey = new HashMap<>(builder.overriddenPluginsPathsByKey);
    this.extraPluginsPathsByKey = new HashMap<>(builder.extraPluginsPathsByKey);
    this.isSonarCloud = builder.isSonarCloud;
  }

  public static Builder sonarQubeBuilder() {
    return new Builder(false);
  }

  public static Builder sonarCloudBuilder() {
    return new Builder(true);
  }

  public boolean isSonarCloud() {
    return isSonarCloud;
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public Map<String, Path> getEmbeddedPluginPathsByKey() {
    return overriddenPluginsPathsByKey;
  }

  public Map<String, Path> getExtraPluginsPathsByKey() {
    return extraPluginsPathsByKey;
  }

  public static final class Builder extends AbstractBuilder<Builder> {
    private String connectionId;
    private Path storageRoot;
    private final Map<String, Path> overriddenPluginsPathsByKey = new HashMap<>();
    private final Map<String, Path> extraPluginsPathsByKey = new HashMap<>();
    private final boolean isSonarCloud;

    private Builder(boolean isSonarCloud) {
      this.isSonarCloud = isSonarCloud;
    }

    /**
     * Unique identifier of the connection. Used for local storage. Only accept a-zA-Z0-9_ characters.
     */
    public Builder setConnectionId(String connectionId) {
      validate(connectionId);
      this.connectionId = connectionId;
      return this;
    }

    private static void validate(@Nullable String connectionId) {
      if (connectionId == null || connectionId.isEmpty()) {
        throw new IllegalArgumentException("'" + connectionId + "' is not a valid connection ID");
      }
    }

    /**
     * Override default storage dir (~/.sonarlint/storage)
     */
    public Builder setStorageRoot(Path storageRoot) {
      this.storageRoot = storageRoot;
      return this;
    }

    /**
     * Register extra embedded plugin to be used in connected mode
     */
    public Builder addExtraPlugin(String pluginKey, Path pluginPath) {
      extraPluginsPathsByKey.put(pluginKey, pluginPath);
      return this;
    }

    /**
     * Ask the engine to prefer the given plugin JAR instead of downloading the one from the server
     */
    public Builder useEmbeddedPlugin(String pluginKey, Path pluginPath) {
      overriddenPluginsPathsByKey.put(pluginKey, pluginPath);
      return this;
    }

    public ConnectedGlobalConfiguration build() {
      return new ConnectedGlobalConfiguration(this);
    }
  }

}
