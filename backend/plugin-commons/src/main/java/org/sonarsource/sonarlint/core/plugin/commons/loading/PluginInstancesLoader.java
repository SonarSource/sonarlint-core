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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import javax.annotation.CheckForNull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.sonar.api.Plugin;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.throwFirstWithOtherSuppressed;
import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.tryAndCollectIOException;

/**
 * Loads the plugin JAR files by creating the appropriate classloaders and by instantiating
 * the entry point classes as defined in manifests. It assumes that JAR files are compatible with current
 * environment (minimal sonarqube version, compatibility between plugins, ...):
 * <ul>
 *   <li>server verifies compatibility of JARs before deploying them at startup (see ServerPluginRepository)</li>
 *   <li>batch loads only the plugins deployed on server (see BatchPluginRepository)</li>
 * </ul>
 * <p>
 * Plugins have their own isolated classloader, inheriting only from API classes.
 * Some plugins can extend a "base" plugin, sharing the same classloader.
 */
public class PluginInstancesLoader implements Closeable {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String[] DEFAULT_SHARED_RESOURCES = {"org/sonar/plugins", "com/sonar/plugins", "com/sonarsource/plugins"};

  private final PluginClassloaderFactory classloaderFactory;
  private final ClassLoader baseClassLoader;
  private final Collection<ClassLoader> classloadersToClose = new ArrayList<>();
  private final List<JarFile> jarFilesToClose = new ArrayList<>();
  private final List<Path> filesToDelete = new ArrayList<>();

  public PluginInstancesLoader() {
    this(new PluginClassloaderFactory());
  }

  PluginInstancesLoader(PluginClassloaderFactory classloaderFactory) {
    this.classloaderFactory = classloaderFactory;
    this.baseClassLoader = getClass().getClassLoader();
  }

  public Map<String, Plugin> instantiatePluginClasses(Collection<PluginInfo> plugins) {
    var defs = defineClassloaders(plugins.stream().collect(Collectors.toMap(PluginInfo::getKey, p -> p)));
    var classloaders = classloaderFactory.create(baseClassLoader, defs);
    this.classloadersToClose.addAll(classloaders.values());
    return instantiatePluginClasses(classloaders);
  }

  /**
   * Defines the different classloaders to be created. Number of classloaders can be
   * different than number of plugins.
   */
  Collection<PluginClassLoaderDef> defineClassloaders(Map<String, PluginInfo> pluginsByKey) {
    Map<String, PluginClassLoaderDef> classloadersByBasePlugin = new HashMap<>();

    for (var info : pluginsByKey.values()) {
      var baseKey = basePluginKey(info, pluginsByKey);
      if (baseKey == null) {
        continue;
      }
      var def = classloadersByBasePlugin.computeIfAbsent(baseKey, PluginClassLoaderDef::new);
      def.addFiles(List.of(info.getJarFile()));
      getJarFile(info.getJarFile().toPath()).ifPresent(jarFilesToClose::add);
      if (!info.getDependencies().isEmpty()) {
        LOG.warn("Plugin '{}' embeds dependencies. This will be deprecated soon. Plugin should be updated.", info.getKey());
        var tmpFolderForDeps = createTmpFolderForPluginDeps(info);
        for (var dependency : info.getDependencies()) {
          var tmpDepFile = extractDependencyInTempFolder(info, dependency, tmpFolderForDeps);
          def.addFiles(List.of(tmpDepFile.toFile()));
          filesToDelete.add(tmpDepFile);
          getJarFile(tmpDepFile).ifPresent(jarFilesToClose::add);
        }
      }
      def.addMainClass(info.getKey(), info.getMainClass());

      for (var defaultSharedResource : DEFAULT_SHARED_RESOURCES) {
        def.getExportMask().addInclusion(String.format("%s/%s/api/", defaultSharedResource, info.getKey()));
      }
    }
    return classloadersByBasePlugin.values();
  }

  /**
   * SLCORE-557 Because of bug <a href="https://bugs.java.com/bugdatabase/view_bug?bug_id=JDK-8315993">JDK-8315993</a> we have to somehow get access
   * to the underlying cached JarFile that will be also opened by the URLClassloader, and close it ourselves.
   */
  private static Optional<JarFile> getJarFile(Path tmpDepFile) {
    try {
      return Optional.of(((JarURLConnection) new URL("jar:" + tmpDepFile.toUri().toURL() + "!/").openConnection()).getJarFile());
    } catch (ZipException ignore) {
      // For tests, we are using fake JARs, so ignore ZipException: zip file is empty
      return Optional.empty();
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static Path createTmpFolderForPluginDeps(PluginInfo info) {
    try {
      var prefix = "sonarlint_" + info.getKey();
      return Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create temporary directory", e);
    }
  }

  private static Path extractDependencyInTempFolder(PluginInfo info, String dependency, Path tempFolder) {
    try {
      var tmpDepFile = tempFolder.resolve(dependency);
      if (!tmpDepFile.startsWith(tempFolder + File.separator)) {
        throw new IOException("Entry is outside of the target dir: " + dependency);
      }
      Files.createDirectories(tmpDepFile.getParent());
      extractFile(info.getJarFile().toPath(), dependency, tmpDepFile);
      return tmpDepFile;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to extract plugin dependency: " + dependency, e);
    }
  }

  private static void extractFile(Path zipFile, String fileName, Path outputFile) throws IOException {
    try (var fileSystem = FileSystems.newFileSystem(zipFile, (ClassLoader) null)) {
      var fileToExtract = fileSystem.getPath(fileName);
      Files.copy(fileToExtract, outputFile);
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
    for (var entry : classloaders.entrySet()) {
      var def = entry.getKey();
      var classLoader = entry.getValue();

      // the same classloader can be used by multiple plugins
      for (var mainClassEntry : def.getMainClassesByPluginKey().entrySet()) {
        var pluginKey = mainClassEntry.getKey();
        var mainClass = mainClassEntry.getValue();
        try {
          instancesByPluginKey.put(pluginKey, (Plugin) classLoader.loadClass(mainClass).getDeclaredConstructor().newInstance());
        } catch (UnsupportedClassVersionError e) {
          LOG.error("The plugin [{}] does not support Java {}", pluginKey, SystemUtils.JAVA_RUNTIME_VERSION, e);
        } catch (Throwable e) {
          LOG.error("Fail to instantiate class [{}] of plugin [{}]", mainClass, pluginKey, e);
        }
      }
    }
    return instancesByPluginKey;
  }

  @Override
  public void close() throws IOException {
    Queue<IOException> exceptions = new LinkedList<>();
    synchronized (classloadersToClose) {
      for (var classLoader : classloadersToClose) {
        if (classLoader instanceof Closeable) {
          tryAndCollectIOException(((Closeable) classLoader)::close, exceptions);
        }
      }
      classloadersToClose.clear();
    }
    synchronized (jarFilesToClose) {
      for (var jarFile : jarFilesToClose) {
        tryAndCollectIOException(jarFile::close, exceptions);
      }
      jarFilesToClose.clear();
    }
    synchronized (filesToDelete) {
      for (var fileToDelete : filesToDelete) {
        tryAndCollectIOException(() -> FileUtils.forceDelete(fileToDelete.toFile()), exceptions);
      }
      filesToDelete.clear();
    }
    throwFirstWithOtherSuppressed(exceptions);
  }

  /**
   * Get the root key of a tree of plugins. For example if plugin C depends on B, which depends on A, then
   * B and C must be attached to the classloader of A. The method returns A in the three cases.
   */
  @CheckForNull
  static String basePluginKey(PluginInfo plugin, Map<String, PluginInfo> allPluginsPerKey) {
    var base = plugin.getKey();
    var parentKey = plugin.getBasePlugin();
    while (isNotEmpty(parentKey)) {
      var parentPlugin = allPluginsPerKey.get(parentKey);
      if (parentPlugin == null) {
        LOG.warn("Unable to find base plugin '{}' referenced by plugin '{}'", parentKey, base);
        return null;
      }
      base = parentPlugin.getKey();
      parentKey = parentPlugin.getBasePlugin();
    }
    return base;
  }
}
