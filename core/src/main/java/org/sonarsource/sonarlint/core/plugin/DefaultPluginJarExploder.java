/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.sonar.api.utils.ZipUtils;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

@SonarLintSide
public class DefaultPluginJarExploder extends PluginJarExploder {

  private final PluginCache fileCache;

  public DefaultPluginJarExploder(PluginCache fileCache) {
    this.fileCache = fileCache;
  }

  @Override
  public ExplodedPlugin explode(PluginInfo info) {
    try {
      File dir = unzipFile(info.getNonNullJarFile());
      return explodeFromUnzippedDir(info.getKey(), info.getNonNullJarFile(), dir);
    } catch (Exception e) {
      throw new IllegalStateException(String.format("Fail to open plugin [%s]: %s", info.getKey(), info.getNonNullJarFile().getAbsolutePath()), e);
    }
  }

  private File unzipFile(File cachedFile) throws IOException {
    String filename = cachedFile.getName();
    File destDir = new File(cachedFile.getParentFile(), filename + "_unzip");
    if (!destDir.exists()) {
      File lockFile = new File(cachedFile.getParentFile(), filename + "_unzip.lock");
      FileOutputStream out = new FileOutputStream(lockFile);
      try (FileLock lock = out.getChannel().lock()) {
        // Recheck in case of concurrent processes
        if (!destDir.exists()) {
          Path tempDir = fileCache.createTempDir();
          ZipUtils.unzip(cachedFile, tempDir.toFile(), newLibFilter());
          FileUtils.moveDirectory(tempDir.toFile(), destDir);
        }
      } finally {
        out.close();
        FileUtils.deleteQuietly(lockFile);
      }
    }
    return destDir;
  }
}
