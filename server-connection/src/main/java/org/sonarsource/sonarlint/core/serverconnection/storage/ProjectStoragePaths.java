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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.commons.codec.binary.Hex;

public class ProjectStoragePaths {

  private static final int MAX_FOLDER_NAME_SIZE = 255;

  public static final String COMPONENT_LIST_PB = "component_list.pb";

  private final Path projectStorageRoot;

  public ProjectStoragePaths(Path projectsStorageRoot) {
    this.projectStorageRoot = projectsStorageRoot;
  }

  public Path getProjectStorageRoot(String projectKey) {
    return projectStorageRoot.resolve(encodeForFs(projectKey));
  }

  /**
   * Encodes a string to be used as a valid file or folder name.
   * It should work in all OS and different names should never collide.
   * See SLCORE-148 and SLCORE-228.
   */
  public static String encodeForFs(String name) {
    var encoded = Hex.encodeHexString(name.getBytes(StandardCharsets.UTF_8));
    if (encoded.length() > MAX_FOLDER_NAME_SIZE) {
      // Most FS will not support a folder name greater than 255
      var md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(name);
      return encoded.substring(0, MAX_FOLDER_NAME_SIZE - md5.length()) + md5;
    }
    return encoded;
  }

  public Path getComponentListPath(String projectKey) {
    return getProjectStorageRoot(projectKey).resolve(COMPONENT_LIST_PB);
  }
}
