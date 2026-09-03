/*
 * SonarLint Core - Medium Tests
 * Copyright (C) SonarSource Sàrl
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
package utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PluginLocator {
  public static final String SONAR_JAVA_PLUGIN_VERSION = "8.41.0.47177";
  public static final String SONAR_JAVA_PLUGIN_JAR = "sonar-java-plugin-" + SONAR_JAVA_PLUGIN_VERSION + ".jar";
  public static final String SONAR_JAVA_PLUGIN_JAR_HASH = "XXX";
  public static final String SONAR_JAVA_SE_PLUGIN_VERSION = "8.16.4.1912";
  public static final String SONAR_JAVA_SE_PLUGIN_JAR = "sonar-java-symbolic-execution-plugin-" + SONAR_JAVA_SE_PLUGIN_VERSION + ".jar";
  public static final String SONAR_JAVA_SE_PLUGIN_JAR_HASH = "unused";

  public static final String SONAR_DBD_PLUGIN_VERSION = "2.7.0.20531";
  public static final String SONAR_DBD_PLUGIN_JAR = "sonar-dbd-plugin-" + SONAR_DBD_PLUGIN_VERSION + ".jar";
  public static final String SONAR_DBD_PLUGIN_JAR_HASH = "unused";
  public static final String SONAR_DBD_JAVA_PLUGIN_VERSION = SONAR_DBD_PLUGIN_VERSION;
  public static final String SONAR_DBD_JAVA_PLUGIN_JAR = "sonar-dbd-java-frontend-plugin-" + SONAR_DBD_JAVA_PLUGIN_VERSION + ".jar";
  public static final String SONAR_DBD_JAVA_PLUGIN_JAR_HASH = "unused";

  public static final String SONAR_JAVASCRIPT_PLUGIN_VERSION = "11.8.0.37897";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR = "sonar-javascript-plugin-" + SONAR_JAVASCRIPT_PLUGIN_VERSION + ".jar";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR_HASH = "XXX";

  public static final String SONAR_PHP_PLUGIN_VERSION = "3.60.0.16641";
  public static final String SONAR_PHP_PLUGIN_JAR = "sonar-php-plugin-" + SONAR_PHP_PLUGIN_VERSION + ".jar";
  public static final String SONAR_PHP_PLUGIN_JAR_HASH = "XXX";

  public static final String SONAR_PYTHON_PLUGIN_VERSION = "5.31.0.36502";
  public static final String SONAR_PYTHON_PLUGIN_JAR = "sonar-python-plugin-" + SONAR_PYTHON_PLUGIN_VERSION + ".jar";
  public static final String SONAR_PYTHON_PLUGIN_JAR_HASH = "XXX";

  public static final String SONAR_KOTLIN_PLUGIN_VERSION = "3.9.0.9809";
  public static final String SONAR_KOTLIN_PLUGIN_JAR = "sonar-kotlin-plugin-" + SONAR_KOTLIN_PLUGIN_VERSION + ".jar";
  public static final String SONAR_KOTLIN_PLUGIN_JAR_HASH = "XXX";

  public static final String SONAR_IAC_ENTERPRISE_PLUGIN_VERSION = "2.11.0.21159";
  public static final String SONAR_IAC_ENTERPRISE_PLUGIN_JAR = "sonar-iac-enterprise-plugin-" + SONAR_IAC_ENTERPRISE_PLUGIN_VERSION + ".jar";
  public static final String SONAR_IAC_ENTERPRISE_PLUGIN_JAR_HASH = "0d405f7b8a964f21eae0e37f5ed11150";

  public static final String SONAR_OMNISHARP_PLUGIN_VERSION = "1.45.0.102027";
  public static final String SONAR_OMNISHARP_PLUGIN_JAR = "sonarlint-omnisharp-plugin-" + SONAR_OMNISHARP_PLUGIN_VERSION + ".jar";
  public static final String SONAR_OMNISHARP_PLUGIN_JAR_HASH = "XXX";

  public static final String SONAR_XML_PLUGIN_VERSION = "2.19.0.8138";
  public static final String SONAR_XML_PLUGIN_JAR = "sonar-xml-plugin-" + SONAR_XML_PLUGIN_VERSION + ".jar";
  public static final String SONAR_XML_PLUGIN_JAR_HASH = "XXX";

  public static final String SONAR_TEXT_PLUGIN_VERSION = "2.49.0.12346";
  public static final String SONAR_TEXT_PLUGIN_JAR = "sonar-text-plugin-" + SONAR_TEXT_PLUGIN_VERSION + ".jar";
  public static final String SONAR_TEXT_PLUGIN_JAR_HASH = "XXX";

  public static final String SONAR_CFAMILY_PLUGIN_VERSION = "6.84.0.101652";
  private static final String SONAR_CFAMILY_PLUGIN_JAR = "sonar-cfamily-plugin-" + SONAR_CFAMILY_PLUGIN_VERSION + ".jar";
  public static final String SONAR_CFAMILY_PLUGIN_JAR_HASH = "XXX";

  public static Path getJavaPluginPath() {
    return getValidPluginPath(SONAR_JAVA_PLUGIN_JAR);
  }

  public static Path getJavaSePluginPath() {
    return getPluginPath(SONAR_JAVA_SE_PLUGIN_JAR);
  }

  public static Path getDbdPluginPath() {
    return getPluginPath(SONAR_DBD_PLUGIN_JAR);
  }

  public static Path getDbdJavaPluginPath() {
    return getPluginPath(SONAR_DBD_JAVA_PLUGIN_JAR);
  }

  public static Path getJavaScriptPluginPath() {
    return getValidPluginPath(SONAR_JAVASCRIPT_PLUGIN_JAR);
  }

  public static Path getPhpPluginPath() {
    return getValidPluginPath(SONAR_PHP_PLUGIN_JAR);
  }

  public static Path getPythonPluginPath() {
    return getValidPluginPath(SONAR_PYTHON_PLUGIN_JAR);
  }

  public static Path getCppPluginPath() {
    return getPluginPath(SONAR_CFAMILY_PLUGIN_JAR);
  }

  public static Path getXmlPluginPath() {
    return getValidPluginPath(SONAR_XML_PLUGIN_JAR);
  }

  public static Path getTextPluginPath() {
    return getValidPluginPath(SONAR_TEXT_PLUGIN_JAR);
  }

  public static Path getKotlinPluginPath() {
    return getPluginPath(SONAR_KOTLIN_PLUGIN_JAR);
  }

  public static Path getIacEnterprisePluginPath() {
    return getPluginPath(SONAR_IAC_ENTERPRISE_PLUGIN_JAR);
  }

  public static Path getOmnisharpPluginPath() {
    return getPluginPath(SONAR_OMNISHARP_PLUGIN_JAR);
  }

  private static Path getPluginPath(String file) {
    return Paths.get("target/plugins/").resolve(file);
  }

  private static Path getValidPluginPath(String file) {
    var path = getPluginPath(file);
    if (!Files.isRegularFile(path)) {
      throw new IllegalStateException("Unable to find file " + path);
    }
    return path;
  }

}
