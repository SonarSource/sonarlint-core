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
package org.sonarsource.sonarlint.core.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;

/**
 * This class loads Sonar plugin metadata from JAR manifest.
 */
public final class PluginManifest {

  public static final String KEY_ATTRIBUTE = "Plugin-Key";
  public static final String MAIN_CLASS_ATTRIBUTE = "Plugin-Class";
  public static final String NAME_ATTRIBUTE = "Plugin-Name";
  public static final String VERSION_ATTRIBUTE = "Plugin-Version";
  public static final String SONAR_VERSION_ATTRIBUTE = "Sonar-Version";
  public static final String DEPENDENCIES_ATTRIBUTE = "Plugin-Dependencies";
  public static final String REQUIRE_PLUGINS_ATTRIBUTE = "Plugin-RequirePlugins";

  /**
   * @since 0.3
   */
  public static final String USE_CHILD_FIRST_CLASSLOADER = "Plugin-ChildFirstClassLoader";

  /**
   * @since 1.1
   */
  public static final String BASE_PLUGIN = "Plugin-Base";

  /**
   * @since 1.3
   */
  public static final String IMPLEMENTATION_BUILD = "Implementation-Build";

  /**
   * @since 1.16
   */
  public static final String SONARLINT_SUPPORTED = "SonarLint-Supported";

  private String key;
  private String name;
  private String mainClass;
  private String version;
  private String sonarVersion;
  private String[] dependencies;
  private boolean useChildFirstClassLoader;
  private String basePlugin;
  private String implementationBuild;
  private String[] requirePlugins;
  private Boolean sonarLintSupported;

  /**
   * Load the manifest from a JAR file.
   */
  public PluginManifest(Path jarPath) throws IOException {
    this();
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      if (jar.getManifest() != null) {
        loadManifest(jar.getManifest());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read plugin manifest from jar : " + jarPath.toAbsolutePath(), e);
    }
  }

  /**
   * @param manifest can not be null
   */
  public PluginManifest(Manifest manifest) {
    this();
    loadManifest(manifest);
  }

  public PluginManifest() {
    dependencies = new String[0];
    useChildFirstClassLoader = false;
    requirePlugins = new String[0];
  }

  private void loadManifest(Manifest manifest) {
    Attributes attributes = manifest.getMainAttributes();
    this.key = PluginKeyUtils.sanitize(attributes.getValue(KEY_ATTRIBUTE));
    this.mainClass = attributes.getValue(MAIN_CLASS_ATTRIBUTE);
    this.name = attributes.getValue(NAME_ATTRIBUTE);
    this.version = attributes.getValue(VERSION_ATTRIBUTE);
    this.sonarVersion = attributes.getValue(SONAR_VERSION_ATTRIBUTE);
    this.useChildFirstClassLoader = StringUtils.equalsIgnoreCase(attributes.getValue(USE_CHILD_FIRST_CLASSLOADER), "true");
    String slSupported = attributes.getValue(SONARLINT_SUPPORTED);
    this.sonarLintSupported = slSupported != null ? StringUtils.equalsIgnoreCase(slSupported, "true") : null;
    this.basePlugin = attributes.getValue(BASE_PLUGIN);
    this.implementationBuild = attributes.getValue(IMPLEMENTATION_BUILD);

    String deps = attributes.getValue(DEPENDENCIES_ATTRIBUTE);
    this.dependencies = StringUtils.split(StringUtils.defaultString(deps), ' ');

    String requires = attributes.getValue(REQUIRE_PLUGINS_ATTRIBUTE);
    this.requirePlugins = StringUtils.split(StringUtils.defaultString(requires), ',');
  }

  public String getKey() {
    return key;
  }

  public PluginManifest setKey(String key) {
    this.key = key;
    return this;
  }

  public String getName() {
    return name;
  }

  public PluginManifest setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * @since 3.5
   */
  public String[] getRequirePlugins() {
    return requirePlugins != null ? requirePlugins.clone() : null;
  }

  /**
   * @since 3.5
   */
  public PluginManifest setRequirePlugins(@Nullable String[] requirePlugins) {
    this.requirePlugins = requirePlugins != null ? requirePlugins.clone() : null;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public PluginManifest setVersion(String version) {
    this.version = version;
    return this;
  }

  public String getSonarVersion() {
    return sonarVersion;
  }

  public PluginManifest setSonarVersion(String sonarVersion) {
    this.sonarVersion = sonarVersion;
    return this;
  }

  public String getMainClass() {
    return mainClass;
  }

  public PluginManifest setMainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  public String[] getDependencies() {
    return dependencies != null ? dependencies.clone() : null;
  }

  public PluginManifest setDependencies(@Nullable String[] dependencies) {
    this.dependencies = dependencies != null ? dependencies.clone() : null;
    return this;
  }

  /**
   * @since 0.3
   */
  public boolean isUseChildFirstClassLoader() {
    return useChildFirstClassLoader;
  }

  /**
   * @since 0.3
   */
  public PluginManifest setUseChildFirstClassLoader(boolean useChildFirstClassLoader) {
    this.useChildFirstClassLoader = useChildFirstClassLoader;
    return this;
  }

  /**
   * @since 1.1
   */
  public String getBasePlugin() {
    return basePlugin;
  }

  /**
   * @since 1.1
   */
  public PluginManifest setBasePlugin(String key) {
    this.basePlugin = key;
    return this;
  }

  /**
   * @since 1.16
   */
  @CheckForNull
  public Boolean isSonarLintSupported() {
    return sonarLintSupported;
  }

  /**
   * @since 1.16
   */
  public PluginManifest setSonarLintSupported(Boolean sonarLintSupported) {
    this.sonarLintSupported = sonarLintSupported;
    return this;
  }

  /**
   * @since 1.3
   */
  public String getImplementationBuild() {
    return implementationBuild;
  }

  /**
   * @since 1.3
   */
  public PluginManifest setImplementationBuild(String implementationBuild) {
    this.implementationBuild = implementationBuild;
    return this;
  }

  @Override
  public String toString() {
    return new ReflectionToStringBuilder(this).toString();
  }

  public boolean isValid() {
    return StringUtils.isNotBlank(key) && StringUtils.isNotBlank(version);
  }

}
