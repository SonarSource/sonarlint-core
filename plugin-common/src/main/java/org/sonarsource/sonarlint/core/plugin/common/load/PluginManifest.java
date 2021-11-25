/*
 * SonarLint Core - Plugin Common
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
package org.sonarsource.sonarlint.core.plugin.common.load;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.plugin.common.Version;

import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * This class loads Sonar plugin metadata from JAR manifest.
 */
public class PluginManifest {

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
  private final String[] dependencies;
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
      String[] fields = StringUtils.split(s, ':');
      return new RequiredPlugin(fields[0], Version.create(fields[1]).removeQualifier());
    }

  }

  /**
   * Load the manifest from a JAR file.
   */
  public static PluginManifest fromJar(Path jarPath) throws IOException {
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      Manifest manifest = jar.getManifest();
      if (manifest != null) {
        return new PluginManifest(manifest);
      } else {
        throw new IllegalStateException("No manifest in jar: " + jarPath.toAbsolutePath());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read plugin manifest from jar: " + jarPath.toAbsolutePath(), e);
    }
  }

  public PluginManifest(Manifest manifest) {
    Attributes attributes = manifest.getMainAttributes();
    this.key = attributes.getValue(KEY_ATTRIBUTE);
    this.mainClass = attributes.getValue(MAIN_CLASS_ATTRIBUTE);
    this.name = attributes.getValue(NAME_ATTRIBUTE);
    this.version = attributes.getValue(VERSION_ATTRIBUTE);
    this.sonarMinVersion = Optional.ofNullable(attributes.getValue(SONAR_VERSION_ATTRIBUTE)).map(Version::create);
    this.basePluginKey = attributes.getValue(BASE_PLUGIN);

    String deps = attributes.getValue(DEPENDENCIES_ATTRIBUTE);
    this.dependencies = StringUtils.split(StringUtils.defaultString(deps), ' ');

    String requires = attributes.getValue(REQUIRE_PLUGINS_ATTRIBUTE);
    this.requiredPlugins = Stream.of(StringUtils.split(StringUtils.defaultString(requires), ',')).map(RequiredPlugin::parse).collect(toUnmodifiableList());
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

  public String[] getDependencies() {
    return dependencies != null ? dependencies.clone() : null;
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
