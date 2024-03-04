/*
 * SonarLint Core - ITs - Tests
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
package its.tools;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class ItUtils {

  public static final String LATEST_RELEASE = "LATEST_RELEASE";
  public static final String SONAR_VERSION = getSonarVersion();
  public static String javaVersion;
  public static String pythonVersion;
  public static String phpVersion;
  public static String javascriptVersion;
  public static String typescriptVersion;
  public static String kotlinVersion;
  public static String rubyVersion;
  public static String scalaVersion;
  public static String webVersion;
  public static String xmlVersion;
  public static String cobolVersion;
  public static String apexVersion;
  public static String tsqlVersion;
  public static String cppVersion;

  static {
    if ("LATEST_RELEASE[7.9]".equals(System.getProperty("sonar.runtimeVersion"))) {
      Properties props = new Properties();
      try (Reader r = Files.newBufferedReader(Paths.get("../../core/src/main/resources/plugins_min_versions.txt"), StandardCharsets.UTF_8)) {
        props.load(r);
        javaVersion = props.getProperty("java");
        pythonVersion = props.getProperty("python");
        phpVersion = props.getProperty("php");
        javascriptVersion = props.getProperty("javascript");
        typescriptVersion = props.getProperty("typescript");
        kotlinVersion = props.getProperty("kotlin");
        rubyVersion = props.getProperty("ruby");
        scalaVersion = props.getProperty("sonarscala");
        webVersion = props.getProperty("web");
        xmlVersion = props.getProperty("xml");
        cobolVersion = props.getProperty("cobol");
        apexVersion = props.getProperty("sonarapex");
        tsqlVersion = props.getProperty("tsql");
        cppVersion = props.getProperty("cpp");
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    } else {
      javaVersion = LATEST_RELEASE;
      pythonVersion = LATEST_RELEASE;
      phpVersion = LATEST_RELEASE;
      javascriptVersion = LATEST_RELEASE;
      typescriptVersion = LATEST_RELEASE;
      kotlinVersion = LATEST_RELEASE;
      rubyVersion = LATEST_RELEASE;
      scalaVersion = LATEST_RELEASE;
      webVersion = LATEST_RELEASE;
      xmlVersion = LATEST_RELEASE;
      cobolVersion = LATEST_RELEASE;
      apexVersion = LATEST_RELEASE;
      tsqlVersion = LATEST_RELEASE;
      cppVersion = LATEST_RELEASE;
    }
  }

  private ItUtils() {
    // utility class, forbidden constructor
  }

  private static String getSonarVersion() {
    String versionProperty = System.getProperty("sonar.runtimeVersion");
    return versionProperty != null ? versionProperty : LATEST_RELEASE;
  }

}
