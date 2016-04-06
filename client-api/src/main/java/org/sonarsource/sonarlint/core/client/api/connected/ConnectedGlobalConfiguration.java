/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.nio.file.Path;
import javax.annotation.CheckForNull;
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

  private final String serverId;
  private final Path storageRoot;

  private ConnectedGlobalConfiguration(Builder builder) {
    super(builder);
    this.serverId = builder.serverId;
    this.storageRoot = builder.storageRoot != null ? builder.storageRoot : getSonarLintUserHome().resolve(DEFAULT_STORAGE_DIR);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  @CheckForNull
  public String getServerId() {
    return serverId;
  }

  public static final class Builder extends AbstractBuilder<Builder> {
    private String serverId;
    private Path storageRoot;

    private Builder() {
    }

    /**
     * Unique identifier for server used for local storage. Only accept a-zA-Z0-9_ characters.
     */
    public Builder setServerId(String serverId) {
      validate(serverId);
      this.serverId = serverId;
      return this;
    }

    private static void validate(String serverId) {
      if (serverId == null || serverId.isEmpty()) {
        throw new IllegalArgumentException("'" + serverId + "' is not a valid server ID");
      }
    }

    /**
     * Override default storage dir (~/.sonarlint/storage)
     */
    public Builder setStorageRoot(Path storageRoot) {
      this.storageRoot = storageRoot;
      return this;
    }

    public ConnectedGlobalConfiguration build() {
      return new ConnectedGlobalConfiguration(this);
    }
  }

}
