/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin.ondemand;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import javax.annotation.Nullable;

public enum DownloadableArtifact {
  CFAMILY_PLUGIN("cpp", "cfamily.version", "/CommercialDistribution/sonar-cfamily-plugin/sonar-cfamily-plugin-%s.jar"),
  CSHARP_OSS("cs", "cs.version", "/Distribution/sonar-csharp-plugin/sonar-csharp-plugin-%s.jar");

  /** System property to override the download URL pattern for all artifacts, e.g. for testing with a mock server. */
  public static final String PROPERTY_URL_PATTERN = "sonarlint.ondemand.url";

  private static final String PROPERTIES_FILE = "ondemand/plugins.properties";
  private static final String BINARIES_URL = "https://binaries.sonarsource.com";
  private static final Properties VERSIONS = loadVersions();

  private final String artifactKey;
  private final String versionKey;
  private final String urlPattern;

  DownloadableArtifact(String artifactKey, String versionKey, String urlPattern) {
    this.artifactKey = artifactKey;
    this.versionKey = versionKey;
    this.urlPattern = urlPattern;
  }

  public static Optional<DownloadableArtifact> byArtifactKey(@Nullable String key) {
    return Arrays.stream(values()).filter(a -> a.artifactKey.equals(key)).findFirst();
  }

  public String version() {
    if (!VERSIONS.containsKey(versionKey)) {
      throw new IllegalStateException("Version is not set in properties for " + artifactKey);
    }
    return VERSIONS.getProperty(versionKey);
  }

  public String urlPattern() {
    var base = System.getProperty(PROPERTY_URL_PATTERN, BINARIES_URL);
    return base + urlPattern;
  }

  public String artifactKey() {
    return artifactKey;
  }

  private static Properties loadVersions() {
    try (var input = DownloadableArtifact.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
      if (input == null) {
        throw new IllegalStateException("Unable to find " + PROPERTIES_FILE + " on classpath");
      }
      var properties = new Properties();
      properties.load(input);
      return properties;
    } catch (IOException e) {
      throw new IllegalStateException("Error loading plugin versions from " + PROPERTIES_FILE, e);
    }
  }
}
