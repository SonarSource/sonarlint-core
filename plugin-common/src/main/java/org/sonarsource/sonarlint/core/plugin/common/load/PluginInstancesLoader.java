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

import java.io.Closeable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.SystemUtils;
import org.sonar.api.Plugin;
import org.sonar.api.utils.log.Loggers;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Loads the plugin JAR files by creating the appropriate classloaders and by instantiating
 * the entry point classes as defined in manifests. It assumes that JAR files are compatible with current
 * environment (minimal sonarqube version, compatibility between plugins, ...).
 * Plugins have their own isolated classloader, inheriting only from API classes.
 * Some plugins can extend a "base" plugin, sharing the same classloader.
 * </p>
 * This class is stateless. It does not keep pointers to classloaders and {@link org.sonar.api.Plugin}.
 */
public class PluginInstancesLoader {

  private static final String[] DEFAULT_SHARED_RESOURCES = {"org/sonar/plugins", "com/sonar/plugins", "com/sonarsource/plugins"};

  private final PluginClassloaderFactory classloaderFactory;

  public PluginInstancesLoader() {
    this(new PluginClassloaderFactory());
  }

  PluginInstancesLoader(PluginClassloaderFactory classloaderFactory) {
    this.classloaderFactory = classloaderFactory;
  }

  public Map<String, Plugin> instantiatePluginClasses(Collection<SonarPluginManifestAndJarPath> plugins) {
    Collection<PluginClassLoaderDef> defs = defineClassloaders(plugins);
    Map<PluginClassLoaderDef, ClassLoader> classloaders = classloaderFactory.create(baseClassLoader(), defs);
    return instantiatePluginClasses(classloaders);
  }

  /**
   * Defines the different classloaders to be created. Number of classloaders can be
   * different than number of plugins.
   */
  Collection<PluginClassLoaderDef> defineClassloaders(Collection<SonarPluginManifestAndJarPath> plugins) {
    Map<String, PluginClassLoaderDef> classloadersByBasePlugin = new HashMap<>();

    for (SonarPluginManifestAndJarPath plugin : plugins) {
      SonarPluginManifest manifest = plugin.getManifest();
      String baseKey = basePluginKey(manifest, plugins.stream().collect(Collectors.toMap(i -> i.getManifest().getKey(), SonarPluginManifestAndJarPath::getManifest)));
      if (baseKey == null) {
        continue;
      }
      PluginClassLoaderDef def = classloadersByBasePlugin.computeIfAbsent(baseKey, PluginClassLoaderDef::new);
      def.addFiles(List.of(plugin.getJarPath()));
      def.addMainClass(manifest.getKey(), manifest.getMainClass());

      for (String defaultSharedResource : DEFAULT_SHARED_RESOURCES) {
        def.getExportMask().addInclusion(String.format("%s/%s/api/", defaultSharedResource, manifest.getKey()));
      }
    }
    return classloadersByBasePlugin.values();
  }

  /**
   * Instantiates collection of {@link org.sonar.api.Plugin} according to given metadata and classloaders
   *
   * @return the instances grouped by plugin key
   * @throws IllegalStateException if at least one plugin can't be correctly loaded
   */
  Map<String, Plugin> instantiatePluginClasses(Map<PluginClassLoaderDef, ClassLoader> classloaders) {
    // instantiate plugins
    Map<String, Plugin> instancesByPluginKey = new HashMap<>();
    for (Map.Entry<PluginClassLoaderDef, ClassLoader> entry : classloaders.entrySet()) {
      PluginClassLoaderDef def = entry.getKey();
      ClassLoader classLoader = entry.getValue();

      // the same classloader can be used by multiple plugins
      for (Map.Entry<String, String> mainClassEntry : def.getMainClassesByPluginKey().entrySet()) {
        String pluginKey = mainClassEntry.getKey();
        String mainClass = mainClassEntry.getValue();
        try {
          instancesByPluginKey.put(pluginKey, (Plugin) classLoader.loadClass(mainClass).getDeclaredConstructor().newInstance());
        } catch (UnsupportedClassVersionError e) {
          throw new IllegalStateException(String.format("The plugin [%s] does not support Java %s",
            pluginKey, SystemUtils.JAVA_RUNTIME_VERSION), e);
        } catch (Throwable e) {
          throw new IllegalStateException(String.format(
            "Fail to instantiate class [%s] of plugin [%s]", mainClass, pluginKey), e);
        }
      }
    }
    return instancesByPluginKey;
  }

  public void unload(Collection<Plugin> plugins) {
    for (Plugin plugin : plugins) {
      ClassLoader classLoader = plugin.getClass().getClassLoader();
      if (classLoader instanceof Closeable && classLoader != baseClassLoader()) {
        try {
          ((Closeable) classLoader).close();
        } catch (Exception e) {
          Loggers.get(getClass()).error("Fail to close classloader " + classLoader.toString(), e);
        }
      }
    }
  }

  private ClassLoader baseClassLoader() {
    return getClass().getClassLoader();
  }

  /**
   * Get the root key of a tree of plugins. For example if plugin C declares B as base plugin, which declares A as base plugin, then
   * B and C must be attached to the classloader of A. The method returns A in the three cases.
   */
  @CheckForNull
  static String basePluginKey(SonarPluginManifest plugin, Map<String, SonarPluginManifest> allPluginsPerKey) {
    String base = plugin.getKey();
    String parentKey = plugin.getBasePluginKey();
    while (isNotEmpty(parentKey)) {
      SonarPluginManifest parentPlugin = allPluginsPerKey.get(parentKey);
      Objects.requireNonNull(parentPlugin, "Unable to find base plugin '" + parentKey + "' referenced by plugin '" + base + "'");
      base = parentPlugin.getKey();
      parentKey = parentPlugin.getBasePluginKey();
    }
    return base;
  }
}
