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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.jar.JarFile;
import org.sonar.api.Plugin;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.throwFirstWithOtherSuppressed;
import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.tryAndCollectIOException;

public class LoadedPlugins implements Closeable {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Map<String, Plugin> pluginInstancesByKeys;

  private final Collection<ClassLoader> classloadersToClose;
  private final Collection<Path> filesToDelete;
  private final List<JarFile> jarFilesToClose;

  public LoadedPlugins(Map<String, Plugin> pluginInstancesByKeys, Collection<ClassLoader> classloadersToClose, List<Path> filesToDelete, List<JarFile> jarFilesToClose) {
    this.pluginInstancesByKeys = pluginInstancesByKeys;
    this.classloadersToClose = new ArrayList<>(classloadersToClose);
    this.filesToDelete = new ArrayList<>(filesToDelete);
    this.jarFilesToClose = jarFilesToClose;
  }

  public Map<String, Plugin> getPluginInstancesByKeys() {
    return pluginInstancesByKeys;
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
        tryAndCollectIOException(() -> Files.delete(fileToDelete), exceptions);
      }
      filesToDelete.clear();
    }
    throwFirstWithOtherSuppressed(exceptions);
  }
}
