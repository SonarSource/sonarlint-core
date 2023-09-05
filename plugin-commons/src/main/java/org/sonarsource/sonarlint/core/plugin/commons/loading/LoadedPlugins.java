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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.sonar.api.Plugin;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.util.ReflectionUtils;

public class LoadedPlugins implements Closeable {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Map<String, Plugin> pluginInstancesByKeys;

  private final Collection<ClassLoader> classloadersToClose;
  private final Collection<Path> filesToDelete;

  public LoadedPlugins(Map<String, Plugin> pluginInstancesByKeys, Collection<ClassLoader> classloadersToClose, List<Path> filesToDelete) {
    this.pluginInstancesByKeys = pluginInstancesByKeys;
    this.classloadersToClose = new ArrayList<>(classloadersToClose);
    this.filesToDelete = new ArrayList<>(filesToDelete);
  }

  public Map<String, Plugin> getPluginInstancesByKeys() {
    return pluginInstancesByKeys;
  }

  @Override
  public void close() throws IOException {
    var exceptions = new ArrayList<IOException>();
    pluginInstancesByKeys.clear();
    synchronized (classloadersToClose) {
      for (var classLoader : classloadersToClose) {
        if (classLoader instanceof Closeable) {
          try {
            ((Closeable) classLoader).close();
          } catch (IOException e) {
            LOG.error("Failed to close classloader", e);
            exceptions.add(e);
          }
        }
      }
      classloadersToClose.clear();
    }
    ReflectionUtils.clearCache();
    try {
      var orderCacheField = OrderUtils.class.getDeclaredField("orderCache");
      orderCacheField.setAccessible(true);
      Map orderCahce = (Map) orderCacheField.get(OrderUtils.class);
      orderCahce.clear();

      Class<?> StandardRepeatableContainersClass = Class.forName("org.springframework.core.annotation.RepeatableContainers$StandardRepeatableContainers");
      var cacheField = StandardRepeatableContainersClass.getDeclaredField("cache");
      cacheField.setAccessible(true);
      Map cache = (Map) cacheField.get(StandardRepeatableContainersClass);
      cache.clear();

      Class<?> attributeMethodClass = Class.forName("org.springframework.core.annotation.AttributeMethods");
      var attCacheField = attributeMethodClass.getDeclaredField("cache");
      attCacheField.setAccessible(true);
      Map attCache = (Map) attCacheField.get(attributeMethodClass);
      attCache.clear();

      Class<?> annotationTypeMappingsClass = Class.forName("org.springframework.core.annotation.AnnotationTypeMappings");
      var noRepeatablesCacheField = annotationTypeMappingsClass.getDeclaredField("noRepeatablesCache");
      noRepeatablesCacheField.setAccessible(true);
      Map noRepeatablesCache = (Map) noRepeatablesCacheField.get(annotationTypeMappingsClass);
      noRepeatablesCache.clear();

      var standardRepeatablesCacheField = annotationTypeMappingsClass.getDeclaredField("standardRepeatablesCache");
      standardRepeatablesCacheField.setAccessible(true);
      Map standardRepeatablesCache = (Map) standardRepeatablesCacheField.get(annotationTypeMappingsClass);
      standardRepeatablesCache.clear();

      Class<?> annotationScannerClass = Class.forName("org.springframework.core.annotation.AnnotationsScanner");
      var declaredAnnotationCacheField = annotationScannerClass.getDeclaredField("declaredAnnotationCache");
      declaredAnnotationCacheField.setAccessible(true);
      Map declaredAnnotationCache = (Map) declaredAnnotationCacheField.get(annotationScannerClass);
      declaredAnnotationCache.clear();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    synchronized (filesToDelete) {
      for (var fileToDelete : filesToDelete) {
        try {
          Files.delete(fileToDelete);
        } catch (IOException e) {
          LOG.error("Failed to delete '{}'", fileToDelete, e);
          exceptions.add(e);
        }
      }
      filesToDelete.clear();
    }

    if (exceptions.isEmpty()) {
      return;
    }
    var firstException = exceptions.remove(0);
    // Suppress any remaining exceptions
    for (var error : exceptions) {
      firstException.addSuppressed(error);
    }
    throw firstException;
  }
}
