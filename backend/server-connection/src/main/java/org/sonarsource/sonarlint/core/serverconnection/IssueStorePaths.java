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
import java.nio.file.Paths;
import javax.annotation.CheckForNull;

import static org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils.toSonarQubePath;

public class IssueStorePaths {

  private IssueStorePaths() {

  }

  @CheckForNull
  public static String idePathToFileKey(ProjectBinding projectBinding, Path ideFilePath) {
    var serverFilePath = idePathToServerPath(projectBinding, ideFilePath);

    if (serverFilePath == null) {
      return null;
    }
    return componentKey(projectBinding, serverFilePath);
  }

  public static String componentKey(ProjectBinding projectBinding, Path serverFilePath) {
    return componentKey(projectBinding.projectKey(), serverFilePath);
  }

  public static String componentKey(String projectKey, Path serverFilePath) {
    return projectKey + ":" + toSonarQubePath(serverFilePath);
  }

  @CheckForNull
  public static Path idePathToServerPath(ProjectBinding projectBinding, Path ideFilePath) {
    return idePathToServerPath(Paths.get(projectBinding.idePathPrefix()), Paths.get(projectBinding.serverPathPrefix()), ideFilePath);
  }

  @CheckForNull
  public static Path idePathToServerPath(Path idePathPrefix, Path serverPathPrefix, Path ideFilePath) {
    Path commonPart;
    if (!idePathPrefix.toString().isEmpty()) {
      if (!ideFilePath.startsWith(idePathPrefix)) {
        return null;
      }
      commonPart = idePathPrefix.relativize(ideFilePath);
    } else {
      commonPart = ideFilePath;
    }
    if (!serverPathPrefix.toString().isEmpty()) {
      return serverPathPrefix.resolve(commonPart);
    } else {
      return commonPart;
    }
  }

}
