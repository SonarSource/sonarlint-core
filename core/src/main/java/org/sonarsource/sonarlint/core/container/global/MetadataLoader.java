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

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.sonar.api.utils.Version;

public class MetadataLoader {

  static final String SONAR_PLUGIN_API_VERSION_FILE_PATH = "/sonar-api-version.txt";
  private static final String SONARLINT_PLUGIN_API_VERSION_FILE_PATH = "/sonarlint-api-version.txt";

  private MetadataLoader() {
    // only static methods
  }

  public static Version loadSonarPluginApiVersion() {
    return loadVersion(SONAR_PLUGIN_API_VERSION_FILE_PATH);
  }

  public static Version loadSonarLintPluginApiVersion() {
    return loadVersion(SONARLINT_PLUGIN_API_VERSION_FILE_PATH);
  }

  private static Version loadVersion(String versionFilePath) {
    return loadVersion(MetadataLoader.class.getResource(versionFilePath), versionFilePath);
  }

  static Version loadVersion(URL versionFileURL, String versionFilePath) {
    try (Scanner scanner = new Scanner(versionFileURL.openStream(), StandardCharsets.UTF_8.name())) {
      String versionInFile = scanner.nextLine();
      return Version.parse(versionInFile);
    } catch (Exception e) {
      throw new IllegalStateException("Can not load " + versionFilePath + " from classpath", e);
    }
  }

}
