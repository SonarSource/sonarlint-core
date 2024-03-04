/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SonarLintCoreVersion {

  private SonarLintCoreVersion() {
  }

  public static String get() {
    String version;
    var packageInfo = SonarLintCoreVersion.class.getPackage();
    if (packageInfo != null && packageInfo.getImplementationVersion() != null) {
      version = packageInfo.getImplementationVersion();
    } else {
      version = getLibraryVersionFallback();
    }
    return version;
  }

  static String getLibraryVersionFallback() {
    var version = "unknown";
    var resource = SonarLintCoreVersion.class.getResourceAsStream("/sl_core_version.txt");
    if (resource != null) {
      try {
        version = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        return version;
      }
    }

    return version;
  }

}
