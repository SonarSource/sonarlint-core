/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin.source.binaries;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.plugins.SonarArtifact;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPluginDependency;

@SuppressWarnings("java:S1192")
public enum BinariesArtifact {

  CFAMILY_PLUGIN(SonarPlugin.C_FAMILY, "cfamily.version", "/CommercialDistribution/sonar-cfamily-plugin/sonar-cfamily-plugin-%s.jar",
    "ondemand/sonar-cpp-plugin.jar.asc"),
  CSHARP_OSS(SonarPlugin.CS_OSS, "cs.version", "/Distribution/sonar-csharp-plugin/sonar-csharp-plugin-%s.jar",
    "ondemand/sonar-cs-plugin.jar.asc"),
  OMNISHARP_MONO(SonarPluginDependency.OMNISHARP_MONO, "omnisharp.version", "/OmniSharp-Roslyn/%s/omnisharp-mono.tar.gz",
    "ondemand/omnisharp-mono.tar.gz.asc"),
  OMNISHARP_NET472(SonarPluginDependency.OMNISHARP_NET472, "omnisharp.version", "/OmniSharp-Roslyn/%s/omnisharp-net472.tar.gz",
    "ondemand/omnisharp-net472.tar.gz.asc"),
  OMNISHARP_NET6(SonarPluginDependency.OMNISHARP_NET6, "omnisharp.version", "/OmniSharp-Roslyn/%s/omnisharp-net6.0.tar.gz",
    "ondemand/omnisharp-net6.0.tar.gz.asc"),
  /**
   * ZIP plugin. The version appears twice in the documented exploded layout, so the pattern uses {@code %1$s}.
   * BUILD-13154 has not published this tree yet.
   */
  JAVASCRIPT(SonarPlugin.JS, "javascript.version",
    "/Distribution/sonar-javascript-plugin/%1$s/plugin/sonar-javascript-plugin-%1$s.jar",
    "ondemand/sonar-javascript-plugin.jar.asc", true),
  PYTHON(SonarPlugin.PYTHON, "python.version",
    "/Distribution/sonar-python-plugin/%1$s/plugin/sonar-python-plugin-%1$s.jar",
    "ondemand/sonar-python-plugin.jar.asc", true);

  /** System property to override the download URL pattern for all artifacts, e.g. for testing with a mock server. */
  public static final String PROPERTY_URL_PATTERN = "sonarlint.ondemand.url";
  public static final String MANIFEST_FILE_NAME = "manifest.json";

  private static final String PROPERTIES_FILE = "ondemand/plugins.properties";
  private static final String BINARIES_URL = "https://binaries.sonarsource.com";
  private static final Properties VERSIONS = loadVersions();
  private static final String TAR_GZ_EXTENSION = ".tar.gz";

  private final SonarArtifact artifact;
  private final String versionKey;
  private final String urlPattern;
  private final String signatureResourcePath;
  private final boolean exploded;

  BinariesArtifact(SonarArtifact artifact, String versionKey, String urlPattern, String signatureResourcePath) {
    this(artifact, versionKey, urlPattern, signatureResourcePath, false);
  }

  BinariesArtifact(SonarArtifact artifact, String versionKey, String urlPattern, String signatureResourcePath, boolean exploded) {
    this.artifact = artifact;
    this.versionKey = versionKey;
    this.urlPattern = urlPattern;
    this.signatureResourcePath = signatureResourcePath;
    this.exploded = exploded;
  }

  public static Optional<BinariesArtifact> findByKey(@Nullable String key) {
    return Arrays.stream(values()).filter(a -> a.artifactKey().equals(key)).findFirst();
  }

  public String version() {
    if (!VERSIONS.containsKey(versionKey)) {
      throw new IllegalStateException("Version is not set in properties for " + artifactKey());
    }
    return VERSIONS.getProperty(versionKey);
  }

  public String urlPattern() {
    var base = System.getProperty(PROPERTY_URL_PATTERN, BINARIES_URL);
    return base + urlPattern;
  }

  public String artifactKey() {
    return artifact.getKey();
  }

  public String signatureResourcePath() {
    return signatureResourcePath;
  }

  public Set<SonarLanguage> getLanguages() {
    return artifact.getLanguages();
  }

  public boolean isArchive() {
    return urlPattern.endsWith(TAR_GZ_EXTENSION);
  }

  /**
   * ZIP plugins are published as an exploded tree: {@code plugin/<artifact>-<version>.jar} plus {@code manifest.json}.
   */
  public boolean isExploded() {
    return exploded;
  }

  public String downloadUrl() {
    return String.format(urlPattern(), version());
  }

  /**
   * URL of {@code manifest.json} beside the {@code plugin/} directory. Only exploded plugins have one.
   */
  public String manifestUrl() {
    return explodedRootUrl() + MANIFEST_FILE_NAME;
  }

  /**
   * URL of one file under {@code resources/}. {@code resourcePath} is appended as published, after the version is filled in.
   */
  public String resourceUrl(String resourcePath) {
    return resourcesRootUrl() + resourcePath;
  }

  private String resourcesRootUrl() {
    var root = explodedRootUrl();
    return root + "resources/";
  }

  /**
   * Local {@code manifest.json} downloaded beside the exploded plugin.
   */
  public Path localManifest(Path onDemandPluginsDirectory) {
    return onDemandPluginsDirectory.resolve(artifactKey()).resolve(version()).resolve(MANIFEST_FILE_NAME);
  }

  private String explodedRootUrl() {
    if (!exploded) {
      throw new IllegalStateException("No exploded tree for " + artifactKey());
    }
    var jarUrl = urlPattern();
    var pluginSegment = "/plugin/";
    var index = jarUrl.lastIndexOf(pluginSegment);
    if (index < 0) {
      throw new IllegalStateException("Exploded plugin URL has no plugin directory: " + jarUrl);
    }
    return String.format(jarUrl.substring(0, index + 1), version());
  }

  public String jarFileName() {
    return "sonar-" + artifactKey() + "-plugin-" + version() + ".jar";
  }

  private static Properties loadVersions() {
    try (var input = BinariesArtifact.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
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
