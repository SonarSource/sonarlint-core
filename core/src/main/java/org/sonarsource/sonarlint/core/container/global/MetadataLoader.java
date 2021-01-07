/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.global;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.Version;

public class MetadataLoader {

  private static final String VERSION_FILE_PATH = "/sonar-api-version.txt";

  private MetadataLoader() {
    // only static methods
  }

  public static Version loadVersion(System2 system) {
    URL url = system.getResource(VERSION_FILE_PATH);

    try (Scanner scanner = new Scanner(url.openStream(), StandardCharsets.UTF_8.name())) {
      String versionInFile = scanner.nextLine();
      return Version.parse(versionInFile);
    } catch (IOException e) {
      throw new IllegalStateException("Can not load " + VERSION_FILE_PATH + " from classpath ", e);
    }
  }

}
