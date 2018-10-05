/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import com.google.common.base.Strings;
import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.sonar.api.Plugin;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.log.Loggers;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * Loads the plugin JAR files by creating the appropriate classloaders and by instantiating
 * the entry point classes as defined in manifests. It assumes that JAR files are compatible with current
 * environment (minimal sonarqube version, compatibility between plugins, ...):
 * <ul>
 *   <li>server verifies compatibility of JARs before deploying them at startup (see ServerPluginRepository)</li>
 *   <li>batch loads only the plugins deployed on server (see BatchPluginRepository)</li>
 * </ul>
 * <p/>
 * Plugins have their own isolated classloader, inheriting only from API classes.
 * Some plugins can extend a "base" plugin, sharing the same classloader.
 * <p/>
 * This class is stateless. It does not keep pointers to classloaders and {@link org.sonar.api.Plugin}.
 */
public class PluginLoader {

  private static final String[] DEFAULT_SHARED_RESOURCES = {"org/sonar/plugins", "com/sonar/plugins", "com/sonarsource/plugins"};
  private static final String SLF4J_ADAPTER_JAR_NAME = "sonarlint-slf4j-sonar-log";

  private final PluginJarExploder jarExploder;
  private final PluginClassloaderFactory classloaderFactory;
  private final TempFolder tempFolder;

  public PluginLoader(PluginJarExploder jarExploder, PluginClassloaderFactory classloaderFactory, TempFolder tempFolder) {
    this.jarExploder = jarExploder;
    this.classloaderFactory = classloaderFactory;
    this.tempFolder = tempFolder;
  }

  public Map<String, Plugin> load(Map<String, PluginInfo> infoByKeys) {
    File slf4jAdapter = extractSlf4jAdapterJar();
    Collection<PluginClassLoaderDef> defs = defineClassloaders(infoByKeys, slf4jAdapter);
    Map<PluginClassLoaderDef, ClassLoader> classloaders = classloaderFactory.create(defs);
    return instantiatePluginClasses(classloaders);
  }

  /**
   * Defines the different classloaders to be created. Number of classloaders can be
   * different than number of plugins.
   */
  Collection<PluginClassLoaderDef> defineClassloaders(Map<String, PluginInfo> infoByKeys, File slf4jAdapter) {
    Map<String, PluginClassLoaderDef> classloadersByBasePlugin = new HashMap<>();

    for (PluginInfo info : infoByKeys.values()) {
      String baseKey = basePluginKey(info, infoByKeys);
      PluginClassLoaderDef def = classloadersByBasePlugin.get(baseKey);
      if (def == null) {
        def = new PluginClassLoaderDef(baseKey);
        classloadersByBasePlugin.put(baseKey, def);
      }
      ExplodedPlugin explodedPlugin = jarExploder.explode(info);
      def.addFiles(Collections.singletonList(slf4jAdapter));
      def.addFiles(Collections.singletonList(explodedPlugin.getMain()));
      def.addFiles(explodedPlugin.getLibs());
      def.addMainClass(info.getKey(), info.getMainClass());

      for (String defaultSharedResource : DEFAULT_SHARED_RESOURCES) {
        def.getExportMask().addInclusion(String.format("%s/%s/api/", defaultSharedResource, info.getKey()));
      }

      // The plugins that extend other plugins can only add some files to classloader.
      // They can't change metadata like ordering strategy or compatibility mode.
      if (Strings.isNullOrEmpty(info.getBasePlugin())) {
        def.setSelfFirstStrategy(info.isUseChildFirstClassLoader());
      }
    }
    return classloadersByBasePlugin.values();
  }

  private File extractSlf4jAdapterJar() {
    InputStream jarInputStream = PluginLoader.class.getResourceAsStream("/" + SLF4J_ADAPTER_JAR_NAME + ".jar");
    try {
      File extractedJar = tempFolder.newFile(SLF4J_ADAPTER_JAR_NAME, ".jar");
      FileUtils.copyInputStreamToFile(jarInputStream, extractedJar);
      return extractedJar;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to extract the jar '" + SLF4J_ADAPTER_JAR_NAME + ".jar'");
    }
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
          instancesByPluginKey.put(pluginKey, (Plugin) classLoader.loadClass(mainClass).newInstance());
        } catch (UnsupportedClassVersionError e) {
          throw new IllegalStateException(String.format("The plugin [%s] does not support Java %s",
            pluginKey, SystemUtils.JAVA_VERSION_TRIMMED), e);
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
      if (classLoader instanceof Closeable && classLoader != classloaderFactory.baseClassLoader()) {
        try {
          ((Closeable) classLoader).close();
        } catch (Exception e) {
          Loggers.get(getClass()).error("Fail to close classloader " + classLoader.toString(), e);
        }
      }
    }
  }

  /**
   * Get the root key of a tree of plugins. For example if plugin C depends on B, which depends on A, then
   * B and C must be attached to the classloader of A. The method returns A in the three cases.
   */
  static String basePluginKey(PluginInfo plugin, Map<String, PluginInfo> allPluginsPerKey) {
    String base = plugin.getKey();
    String parentKey = plugin.getBasePlugin();
    while (isNotEmpty(parentKey)) {
      PluginInfo parentPlugin = allPluginsPerKey.get(parentKey);
      base = parentPlugin.getKey();
      parentKey = parentPlugin.getBasePlugin();
    }
    return base;
  }
}
