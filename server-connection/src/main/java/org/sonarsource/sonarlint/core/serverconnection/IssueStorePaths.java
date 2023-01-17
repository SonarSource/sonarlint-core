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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

public class IssueStorePaths {

  private IssueStorePaths() {

  }

  @CheckForNull
  public static String idePathToFileKey(ProjectBinding projectBinding, String ideFilePath) {
    var serverFilePath = idePathToServerPath(projectBinding, ideFilePath);

    if (serverFilePath == null) {
      return null;
    }
    return componentKey(projectBinding, serverFilePath);
  }

  public static String componentKey(ProjectBinding projectBinding, String serverFilePath) {
    return projectBinding.projectKey() + ":" + serverFilePath;
  }

  @CheckForNull
  public static String idePathToServerPath(ProjectBinding projectBinding, String ideFilePathStr) {
    var ideFilePath = Paths.get(ideFilePathStr);
    Path commonPart;
    if (StringUtils.isNotEmpty(projectBinding.idePathPrefix())) {
      var idePathPrefix = Paths.get(projectBinding.idePathPrefix());
      if (!ideFilePath.startsWith(idePathPrefix)) {
        return null;
      }
      commonPart = idePathPrefix.relativize(ideFilePath);
    } else {
      commonPart = ideFilePath;
    }
    if (StringUtils.isNotEmpty(projectBinding.serverPathPrefix())) {
      var serverPathPrefix = Paths.get(projectBinding.serverPathPrefix());
      return FilenameUtils.separatorsToUnix(serverPathPrefix.resolve(commonPart).toString());
    } else {
      return FilenameUtils.separatorsToUnix(commonPart.toString());
    }
  }

}
