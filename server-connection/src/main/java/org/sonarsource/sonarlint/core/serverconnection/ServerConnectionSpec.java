/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.Language;

public class ServerConnectionSpec {
  public final Set<Language> enabledLanguages;
  public final Set<String> embeddedPluginKeys;
  public final Path workDir;
  public final Path globalStorageRoot;
  public final String connectionId;
  public final boolean isSonarCloud;


  public ServerConnectionSpec(Path globalStorageRoot, String connectionId, boolean isSonarCloud, Set<Language> enabledLanguages, Set<String> embeddedPluginKeys, Path workDir) {
    this.globalStorageRoot = globalStorageRoot;
    this.connectionId = connectionId;
    this.isSonarCloud = isSonarCloud;
    this.enabledLanguages = enabledLanguages;
    this.embeddedPluginKeys = embeddedPluginKeys;
    this.workDir = workDir;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ServerConnectionSpec that = (ServerConnectionSpec) o;
    return isSonarCloud == that.isSonarCloud
      && enabledLanguages.equals(that.enabledLanguages)
      && embeddedPluginKeys.equals(that.embeddedPluginKeys)
      && workDir.equals(that.workDir)
      && globalStorageRoot.equals(that.globalStorageRoot)
      && connectionId.equals(that.connectionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabledLanguages, embeddedPluginKeys, workDir, globalStorageRoot, connectionId, isSonarCloud);
  }
}
