/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.file;

import java.nio.file.Path;

public class FilePathTranslation {
  private final Path idePathPrefix;
  private final Path serverPathPrefix;

  public FilePathTranslation(Path idePathPrefix, Path serverPathPrefix) {
    this.idePathPrefix = idePathPrefix;
    this.serverPathPrefix = serverPathPrefix;
  }

  public Path getIdePathPrefix() {
    return idePathPrefix;
  }

  public Path getServerPathPrefix() {
    return serverPathPrefix;
  }

  public Path serverToIdePath(Path serverFilePath) {
    if (!serverFilePath.startsWith(serverPathPrefix)) {
      return serverFilePath;
    }
    var localPrefixLen = serverPathPrefix.toString().length();
    if (localPrefixLen > 0) {
      localPrefixLen++;
    }
    return idePathPrefix.resolve(serverFilePath.toString().substring(localPrefixLen));
  }

  public Path ideToServerPath(Path idePath) {
    if (!idePath.startsWith(idePathPrefix)) {
      return idePath;
    }
    var localPrefixLen = idePathPrefix.toString().length();
    if (localPrefixLen > 0) {
      localPrefixLen++;
    }
    return serverPathPrefix.resolve(idePath.toString().substring(localPrefixLen));
  }

}
