/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PluginLocator {

  public static final String SONAR_JAVA_PLUGIN_JAR = "sonar-java-plugin-4.8.0.9441.jar";
  public static final String SONAR_JAVA_PLUGIN_JAR_HASH = "dc49ad9b282b7881abcd24e84dc26f0c";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR = "sonar-javascript-plugin-2.21.1.4786.jar";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR_HASH = "2e0b156659bb6f60e11dfcb5323080ea";
  public static final String SONAR_XOO_PLUGIN_NAME = "sonar-xoo-plugin";

  public static URL getJavaPluginUrl() {
    return getPluginUrl(SONAR_JAVA_PLUGIN_JAR);
  }

  public static URL getJavaScriptPluginUrl() {
    return getPluginUrl(SONAR_JAVASCRIPT_PLUGIN_JAR);
  }

  public static URL getPhpPluginUrl() {
    return getPluginUrl("sonar-php-plugin-2.10.0.2087.jar");
  }

  public static URL getPythonPluginUrl() {
    return getPluginUrl("sonar-python-plugin-1.7.0.1195.jar");
  }

  public static URL getCppPluginUrl() {
    return getPluginUrl("sonar-cfamily-plugin-4.11.0.8388.jar");
  }

  public static URL getXooPluginUrl() {
    return getPluginUrlUnknownVersion(SONAR_XOO_PLUGIN_NAME);
  }

  private static URL getPluginUrl(String file) {
    try {
      return new File("target/plugins/" + file).getAbsoluteFile().toURI().toURL();
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Tries to find a plugin with a name starting with the provided string.
   * If no plugin is found, NoSuchElementException is thrown.
   */
  public static URL getPluginUrlUnknownVersion(String name) {
    try {
      Path dir = Paths.get("target/plugins/");
      Path plugin = Files.list(dir)
        .filter(x -> x.getFileName().toString().startsWith(name))
        .findAny()
        .get();
      return plugin.toUri().toURL();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

}
