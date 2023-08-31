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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.sonar.api.Plugin;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class LoadedPlugins implements Closeable {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Map<String, Plugin> pluginInstancesByKeys;

  private final Collection<ClassLoader> classloadersToClose;
  private final List<Path> filesToDelete;

  public LoadedPlugins(Map<String, Plugin> pluginInstancesByKeys, Collection<ClassLoader> classloadersToClose, List<Path> filesToDelete) {
    this.pluginInstancesByKeys = pluginInstancesByKeys;
    this.classloadersToClose = classloadersToClose;
    this.filesToDelete = filesToDelete;
  }

  public Map<String, Plugin> getPluginInstancesByKeys() {
    return pluginInstancesByKeys;
  }

  @Override
  public void close() throws IOException {
    var exceptions = new ArrayList<Exception>();
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
    for (var fileToDelete : filesToDelete) {
      try {
        FileUtils.forceDelete(fileToDelete.toFile());
      } catch (IOException e) {
        LOG.error("Failed to delete '{}'", fileToDelete, e);
        exceptions.add(e);
      }
    }
    if (!exceptions.isEmpty()) {
      var exception = new IOException("Unable to properly close plugins instances");
      exceptions.forEach(exception::addSuppressed);
      throw exception;
    }
  }
}
