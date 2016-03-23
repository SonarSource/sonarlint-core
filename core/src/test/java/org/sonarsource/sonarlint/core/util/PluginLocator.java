/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

public class PluginLocator {

  public static final String SONAR_JAVA_PLUGIN_JAR = "sonar-java-plugin-3.11.jar";
  public static final String SONAR_JAVA_PLUGIN_JAR_HASH = "cf57e66593fd6ff127f44e7652043712";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR = "sonar-javascript-plugin-2.11.jar";
  public static final String SONAR_JAVASCRIPT_PLUGIN_JAR_HASH = "0919c31699187c485d792363ea363aba";

  public static URL getJavaPluginUrl() {
    return getPluginUrl(SONAR_JAVA_PLUGIN_JAR);
  }

  public static URL getJavaScriptPluginUrl() {
    return getPluginUrl(SONAR_JAVASCRIPT_PLUGIN_JAR);
  }

  public static URL getPhpPluginUrl() {
    return getPluginUrl("sonar-php-plugin-2.8.jar");
  }

  private static URL getPluginUrl(String file) {
    try {
      return new File("target/plugins/" + file).getAbsoluteFile().toURI().toURL();
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

}
