/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package testutils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PluginLocator {

  public static final String SONAR_JAVA_PLUGIN_JAR = "sonar-java-plugin-6.0.0.20538.jar";
  public static final String SONAR_JAVA_PLUGIN_JAR_HASH = "eb27aea472a0d7d91ed529086ce8ee1c";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR = "sonar-javascript-plugin-6.5.0.13383.jar";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR_HASH = "2fab92be44e07f1d367f891a55258736";
  public static final String SONAR_PHP_PLUGIN_JAR = "sonar-php-plugin-3.2.0.4868.jar";
  public static final String SONAR_PYTHON_PLUGIN_JAR = "sonar-python-plugin-1.14.0.3086.jar";

  public static Path getJavaPluginPath() {
    return getPluginPath(SONAR_JAVA_PLUGIN_JAR);
  }

  public static Path getJavaScriptPluginPath() {
    return getPluginPath(SONAR_JAVASCRIPT_PLUGIN_JAR);
  }

  public static Path getPhpPluginPath() {
    return getPluginPath(SONAR_PHP_PLUGIN_JAR);
  }

  public static Path getPythonPluginPath() {
    return getPluginPath(SONAR_PYTHON_PLUGIN_JAR);
  }

  public static Path getCppPluginPath() {
    return getPluginPath("sonar-cfamily-plugin-6.18.0.29274.jar");
  }

  private static Path getPluginPath(String file) {
    return Paths.get("target/plugins/").resolve(file);
  }

}
