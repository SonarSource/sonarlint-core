/*
 * SonarLint Core - Server Connection
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverapi.plugins.StaticResource;

public class StoredPlugin {
  private final String key;
  private final String hash;
  private final Path jarPath;
  private final String staticResourceFolder;
  private final List<StaticResource> staticResources;

  public StoredPlugin(String key, String hash, Path jarPath) {
    this(key, hash, jarPath, null, List.of());
  }

  public StoredPlugin(String key, String hash, Path jarPath, @Nullable String staticResourceFolder, List<StaticResource> staticResources) {
    this.key = key;
    this.hash = hash;
    this.jarPath = jarPath;
    this.staticResourceFolder = staticResourceFolder;
    this.staticResources = List.copyOf(staticResources);
  }

  public String getKey() {
    return key;
  }

  public String getHash() {
    return hash;
  }

  public Path getJarPath() {
    return jarPath;
  }

  public boolean hasSameHash(ServerPlugin serverPlugin) {
    return getHash().equals(serverPlugin.getHash());
  }

  @Nullable
  public String getStaticResourceFolder() {
    return staticResourceFolder;
  }

  public List<StaticResource> getStaticResources() {
    return staticResources;
  }
}
