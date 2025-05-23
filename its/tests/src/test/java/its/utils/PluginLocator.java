/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2025 SonarSource SA
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
package its.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class PluginLocator {

  public static Path getCppPluginPath() {
    return getPluginPath("sonar-cfamily-plugin-6.41.2.69583.jar");
  }

  public static Path getGoPluginPath() {
    return getPluginPath("sonar-go-plugin-1.23.1.2838.jar");
  }

  public static Path getIacPluginPath() {
    return getPluginPath("sonar-iac-plugin-1.16.0.3845.jar");
  }

  public static Path getJavascriptPluginPath() {
    return getPluginPath("sonar-javascript-plugin-10.12.0.25537.jar");
  }

  public static Map<String, Path> getEmbeddedPluginsByKeyForTests() {
    return Map.of(
      "javascript", getJavascriptPluginPath(),
      "go", PluginLocator.getGoPluginPath(),
      "iac", PluginLocator.getIacPluginPath());
  }

  private static Path getPluginPath(String file) {
    return Paths.get("target/plugins/").resolve(file);
  }

}
