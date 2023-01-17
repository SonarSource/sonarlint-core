/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons.loading;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.commons.Version;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * This class loads Sonar plugin metadata from JAR manifest.
 */
public class SonarPluginManifest {

  public static final String KEY_ATTRIBUTE = "Plugin-Key";
  public static final String MAIN_CLASS_ATTRIBUTE = "Plugin-Class";
  public static final String NAME_ATTRIBUTE = "Plugin-Name";
  public static final String VERSION_ATTRIBUTE = "Plugin-Version";
  public static final String SONAR_VERSION_ATTRIBUTE = "Sonar-Version";
  public static final String DEPENDENCIES_ATTRIBUTE = "Plugin-Dependencies";
  public static final String REQUIRE_PLUGINS_ATTRIBUTE = "Plugin-RequirePlugins";
  public static final String BASE_PLUGIN = "Plugin-Base";
  public static final String JRE_MIN_VERSION = "Jre-Min-Version";
  public static final String NODEJS_MIN_VERSION = "NodeJs-Min-Version";

  private final String key;
  private final String name;
  private final String mainClass;
  private final String version;
  private final Optional<Version> sonarMinVersion;
  private final List<String> dependencies;
  private final String basePluginKey;
  private final List<RequiredPlugin> requiredPlugins;
  private final Optional<Version> jreMinVersion;
  private final Optional<Version> nodeJsMinVersion;

  public static class RequiredPlugin {

    private static final Pattern PARSER = Pattern.compile("\\w+:.+");

    private final String key;
    private final Version minimalVersion;

    public RequiredPlugin(String key, Version minimalVersion) {
      this.key = key;
      this.minimalVersion = minimalVersion;
    }

    public String getKey() {
      return key;
    }

    public Version getMinimalVersion() {
      return minimalVersion;
    }

    public static RequiredPlugin parse(String s) {
      if (!PARSER.matcher(s).matches()) {
        throw new IllegalArgumentException("Manifest field does not have correct format: " + s);
      }
      var fields = StringUtils.split(s, ':');
      return new RequiredPlugin(fields[0], Version.create(fields[1]).removeQualifier());
    }

  }

  /**
   * Load the manifest from a JAR file.
   */
  public static SonarPluginManifest fromJar(Path jarPath) {
    try (var jar = new JarFile(jarPath.toFile())) {
      var manifest = jar.getManifest();
      if (manifest != null) {
        return new SonarPluginManifest(manifest);
      } else {
        throw new IllegalStateException("No manifest in jar: " + jarPath.toAbsolutePath());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error while reading plugin manifest from jar: " + jarPath.toAbsolutePath(), e);
    }
  }

  public SonarPluginManifest(Manifest manifest) {
    var attributes = manifest.getMainAttributes();
    this.key = requireNonNull(attributes.getValue(KEY_ATTRIBUTE), "Plugin key is mandatory");
    this.mainClass = attributes.getValue(MAIN_CLASS_ATTRIBUTE);
    this.name = attributes.getValue(NAME_ATTRIBUTE);
    this.version = attributes.getValue(VERSION_ATTRIBUTE);
    this.sonarMinVersion = Optional.ofNullable(attributes.getValue(SONAR_VERSION_ATTRIBUTE)).map(Version::create);
    this.basePluginKey = attributes.getValue(BASE_PLUGIN);

    var deps = attributes.getValue(DEPENDENCIES_ATTRIBUTE);
    this.dependencies = List.of(StringUtils.split(StringUtils.defaultString(deps), ' '));

    var requires = attributes.getValue(REQUIRE_PLUGINS_ATTRIBUTE);
    this.requiredPlugins = Stream.of(StringUtils.split(StringUtils.defaultString(requires), ',')).map(RequiredPlugin::parse).collect(toList());
    this.jreMinVersion = Optional.ofNullable(attributes.getValue(JRE_MIN_VERSION)).map(Version::create);
    this.nodeJsMinVersion = Optional.ofNullable(attributes.getValue(NODEJS_MIN_VERSION)).map(Version::create);
  }

  public String getKey() {
    return key;
  }

  @CheckForNull
  public String getName() {
    return name;
  }

  public List<RequiredPlugin> getRequiredPlugins() {
    return requiredPlugins;
  }

  @CheckForNull
  public String getVersion() {
    return version;
  }

  public Optional<Version> getSonarMinVersion() {
    return sonarMinVersion;
  }

  public String getMainClass() {
    return mainClass;
  }

  public List<String> getDependencies() {
    return dependencies;
  }

  @CheckForNull
  public String getBasePluginKey() {
    return basePluginKey;
  }

  public Optional<Version> getJreMinVersion() {
    return jreMinVersion;
  }

  public Optional<Version> getNodeJsMinVersion() {
    return nodeJsMinVersion;
  }

}
