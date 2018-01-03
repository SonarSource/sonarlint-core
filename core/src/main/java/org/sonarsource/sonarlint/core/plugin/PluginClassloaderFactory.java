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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.sonar.classloader.ClassloaderBuilder;
import org.sonar.classloader.Mask;
import org.sonarsource.api.sonarlint.SonarLintSide;

import static org.sonar.classloader.ClassloaderBuilder.LoadingOrder.PARENT_FIRST;
import static org.sonar.classloader.ClassloaderBuilder.LoadingOrder.SELF_FIRST;

/**
 * Builds the graph of classloaders to be used to instantiate plugins. It deals with:
 * <ul>
 *   <li>isolation of plugins against core classes (except api)</li>
 *   <li>backward-compatibility with plugins built for versions of SQ lower than 5.2. At that time
 *   API declared transitive dependencies that were automatically available to plugins</li>
 *   <li>sharing of some packages between plugins</li>
 *   <li>loading of the libraries embedded in plugin JAR files (directory META-INF/libs)</li>
 * </ul>
 */
@SonarLintSide
public class PluginClassloaderFactory {

  // underscores are used to not conflict with plugin keys (if someday a plugin key is "api")
  private static final String API_CLASSLOADER_KEY = "_api_";

  /**
   * Creates as many classloaders as requested by the input parameter.
   */
  public Map<PluginClassLoaderDef, ClassLoader> create(Collection<PluginClassLoaderDef> defs) {
    ClassLoader baseClassLoader = baseClassLoader();

    ClassloaderBuilder builder = new ClassloaderBuilder();
    builder.newClassloader(API_CLASSLOADER_KEY, baseClassLoader);
    builder.setMask(API_CLASSLOADER_KEY, apiMask());

    for (PluginClassLoaderDef def : defs) {
      builder.newClassloader(def.getBasePluginKey());
      builder.setParent(def.getBasePluginKey(), API_CLASSLOADER_KEY, new Mask());
      builder.setLoadingOrder(def.getBasePluginKey(), def.isSelfFirstStrategy() ? SELF_FIRST : PARENT_FIRST);
      for (File jar : def.getFiles()) {
        builder.addURL(def.getBasePluginKey(), fileToUrl(jar));
      }
      exportResources(def, builder, defs);
    }

    return build(defs, builder);
  }

  /**
   * A plugin can export some resources to other plugins
   */
  private static void exportResources(PluginClassLoaderDef def, ClassloaderBuilder builder, Collection<PluginClassLoaderDef> allPlugins) {
    // export the resources to all other plugins
    builder.setExportMask(def.getBasePluginKey(), def.getExportMask());
    for (PluginClassLoaderDef other : allPlugins) {
      if (!other.getBasePluginKey().equals(def.getBasePluginKey())) {
        builder.addSibling(def.getBasePluginKey(), other.getBasePluginKey(), new Mask());
      }
    }
  }

  /**
   * Builds classloaders and verifies that all of them are correctly defined
   */
  private static Map<PluginClassLoaderDef, ClassLoader> build(Collection<PluginClassLoaderDef> defs, ClassloaderBuilder builder) {
    Map<PluginClassLoaderDef, ClassLoader> result = new HashMap<>();
    Map<String, ClassLoader> classloadersByBasePluginKey = builder.build();
    for (PluginClassLoaderDef def : defs) {
      ClassLoader classloader = classloadersByBasePluginKey.get(def.getBasePluginKey());
      if (classloader == null) {
        throw new IllegalStateException(String.format("Fail to create classloader for plugin [%s]", def.getBasePluginKey()));
      }
      result.put(def, classloader);
    }
    return result;
  }

  ClassLoader baseClassLoader() {
    return getClass().getClassLoader();
  }

  private static URL fileToUrl(File file) {
    try {
      return file.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * The resources (packages) that API exposes to plugins. Other core classes (SonarQube, MyBatis, ...)
   * can't be accessed.
   * <p>To sum-up, these are the classes packaged in sonar-plugin-api.jar or available as
   * a transitive dependency of sonar-plugin-api</p>
   */
  private static Mask apiMask() {
    return new Mask()
      .addInclusion("org/sonar/api/")
      .addInclusion("org/sonarsource/api/sonarlint/")
      .addInclusion("org/sonar/check/")
      // Used by SonarCobol
      .addInclusion("org/sonar/channel/")
      .addInclusion("net/sourceforge/pmd/")

      // API exclusions
      .addExclusion("org/sonar/api/internal/");
  }
}
