/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.plugins;

import java.util.List;
import javax.annotation.Nullable;

public class ServerPlugin {
  private final String key;
  private final String hash;
  private final String filename;
  private final boolean sonarLintSupported;
  private final String staticResourceFolder;
  private final List<StaticResource> staticResources;

  public ServerPlugin(String key, String hash, String filename, boolean sonarLintSupported) {
    this(key, hash, filename, sonarLintSupported, null, List.of());
  }

  public ServerPlugin(String key, String hash, String filename, boolean sonarLintSupported, @Nullable String staticResourceFolder,
    List<StaticResource> staticResources) {
    this.key = key;
    this.hash = hash;
    this.filename = filename;
    this.sonarLintSupported = sonarLintSupported;
    this.staticResourceFolder = staticResourceFolder;
    this.staticResources = List.copyOf(staticResources);
  }

  public ServerPlugin withFilename(String filename) {
    return new ServerPlugin(key, hash, filename, sonarLintSupported, staticResourceFolder, staticResources);
  }

  public String getKey() {
    return key;
  }

  public String getHash() {
    return hash;
  }

  public String getFilename() {
    return filename;
  }

  public boolean isSonarLintSupported() {
    return sonarLintSupported;
  }

  @Nullable
  public String getStaticResourceFolder() {
    return staticResourceFolder;
  }

  public List<StaticResource> getStaticResources() {
    return staticResources;
  }
}
