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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;

/**
 * To use SonarLint in connected mode please provide a server id that will identify the storage.
 * To use in standalone mode please provide list of plugin URLs.
 *
 */
@Immutable
public class ConnectedGlobalConfiguration extends AbstractGlobalConfiguration {

  public static final String DEFAULT_STORAGE_DIR = "storage";

  private final String connectionId;
  private final Path storageRoot;
  private final Map<String, URL> overriddenPluginsUrlsByKey;
  private final Map<String, URL> extraPluginsUrlsByKey;

  private ConnectedGlobalConfiguration(Builder builder) {
    super(builder);
    this.connectionId = builder.connectionId;
    this.storageRoot = builder.storageRoot != null ? builder.storageRoot : getSonarLintUserHome().resolve(DEFAULT_STORAGE_DIR);
    this.overriddenPluginsUrlsByKey = new HashMap<>(builder.overriddenPluginsUrlsByKey);
    this.extraPluginsUrlsByKey = new HashMap<>(builder.extraPluginsUrlsByKey);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public Map<String, URL> getEmbeddedPluginUrlsByKey() {
    return overriddenPluginsUrlsByKey;
  }

  public Map<String, URL> getExtraPluginsUrlsByKey() {
    return extraPluginsUrlsByKey;
  }

  public static final class Builder extends AbstractBuilder<Builder> {
    private String connectionId;
    private Path storageRoot;
    private final Map<String, URL> overriddenPluginsUrlsByKey = new HashMap<>();
    private final Map<String, URL> extraPluginsUrlsByKey = new HashMap<>();

    private Builder() {
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
    public Builder addExtraPlugin(String pluginKey, URL pluginUrl) {
      extraPluginsUrlsByKey.put(pluginKey, pluginUrl);
      return this;
    }

    /**
     * Ask the engine to prefer the given plugin JAR instead of downloading the one from the server
     */
    public Builder useEmbeddedPlugin(String pluginKey, URL pluginUrl) {
      overriddenPluginsUrlsByKey.put(pluginKey, pluginUrl);
      return this;
    }

    public ConnectedGlobalConfiguration build() {
      return new ConnectedGlobalConfiguration(this);
    }
  }

}
