/*
 * SonarLint Core - Medium Tests
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
package testutils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PluginLocator {
  public static final String SONAR_JAVA_PLUGIN_VERSION = "7.15.0.30507";
  public static final String SONAR_JAVA_PLUGIN_JAR = "sonar-java-plugin-" + SONAR_JAVA_PLUGIN_VERSION + ".jar";
  public static final String SONAR_JAVA_PLUGIN_JAR_HASH = "fa425ffda3272aef1abc137941a64772";
  public static final String SONAR_JAVASCRIPT_PLUGIN_VERSION = "9.6.0.18814";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR = "sonar-javascript-plugin-" + SONAR_JAVASCRIPT_PLUGIN_VERSION + ".jar";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR_HASH = "2fab92be44e07f1d367f891a55258736";
  public static final String SONAR_PHP_PLUGIN_VERSION = "3.23.1.8766";
  public static final String SONAR_PHP_PLUGIN_JAR = "sonar-php-plugin-" + SONAR_PHP_PLUGIN_VERSION + ".jar";
  public static final String SONAR_PHP_PLUGIN_JAR_HASH = "88ddaa391f3176891a62375e98b76ae9";
  public static final String SONAR_PYTHON_PLUGIN_VERSION = "4.1.0.11333";
  public static final String SONAR_PYTHON_PLUGIN_JAR = "sonar-python-plugin-" + SONAR_PYTHON_PLUGIN_VERSION + ".jar";
  public static final String SONAR_PYTHON_PLUGIN_JAR_HASH = "e1cff9e38811ab71e6efbff087743367";
  public static final String SONAR_XML_PLUGIN_JAR = "sonar-xml-plugin-2.6.1.3686.jar";
  public static final String SONAR_TEXT_PLUGIN_JAR = "sonar-text-plugin-2.0.1.611.jar";
  public static final String SONAR_KOTLIN_PLUGIN_JAR = "sonar-kotlin-plugin-2.20.0.4382.jar";

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

  public static Path getXmlPluginPath() {
    return getPluginPath(SONAR_XML_PLUGIN_JAR);
  }

  public static Path getTextPluginPath() {
    return getPluginPath(SONAR_TEXT_PLUGIN_JAR);
  }

  public static Path getKotlinPluginPath() {
    return getPluginPath(SONAR_KOTLIN_PLUGIN_JAR);
  }

  private static Path getPluginPath(String file) {
    var path = Paths.get("target/plugins/").resolve(file);
    if (!Files.isRegularFile(path)) {
      throw new IllegalStateException("Unable to find file " + path);
    }
    return path;
  }

}
