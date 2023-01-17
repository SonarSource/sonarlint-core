/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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

public class ServerPlugin {
  private final String key;
  private final String hash;
  private final String filename;
  private final boolean sonarLintSupported;

  public ServerPlugin(String key, String hash, String filename, boolean sonarLintSupported) {
    this.key = key;
    this.hash = hash;
    this.filename = filename;
    this.sonarLintSupported = sonarLintSupported;
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
}
