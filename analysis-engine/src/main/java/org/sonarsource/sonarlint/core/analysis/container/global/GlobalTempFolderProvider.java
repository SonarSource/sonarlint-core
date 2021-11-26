/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.picocontainer.ComponentLifecycle;
import org.picocontainer.PicoContainer;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.api.GlobalAnalysisConfiguration;

public class GlobalTempFolderProvider extends ProviderAdapter implements ComponentLifecycle<TempFolder> {

  private static final Logger LOG = Loggers.get(GlobalTempFolderProvider.class);
  private static final long CLEAN_MAX_AGE = TimeUnit.DAYS.toMillis(21);
  static final String TMP_NAME_PREFIX = "sonarlint_tmp_";
  private boolean started = false;

  private GlobalTempFolder tempFolder;

  public GlobalTempFolder provide(GlobalAnalysisConfiguration globalConfiguration) {
    if (tempFolder == null) {

      Path workingPath = globalConfiguration.getWorkDir();

      if (workingPath != null) {
        tryCleanPreviousTempFolders(workingPath);
      }

      Path tempDir = createTempFolder(workingPath);
      tempFolder = new GlobalTempFolder(tempDir.toFile(), true);
    }
    return tempFolder;
  }

  private static Path createTempFolder(@Nullable Path workingPath) {
    try {
      if (workingPath != null) {
        Files.createDirectories(workingPath);
        return Files.createTempDirectory(workingPath, TMP_NAME_PREFIX);
      } else {
        return Files.createTempDirectory(TMP_NAME_PREFIX);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create temporary folder", e);
    }
  }

  private static void tryCleanPreviousTempFolders(Path path) {
    try {
      if (Files.exists(path)) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, new CleanFilter())) {
          for (Path p : stream) {
            FileUtils.deleteQuietly(p.toFile());
          }
        }
      }
    } catch (IOException e) {
      LOG.error(String.format("failed to clean global working directory: %s", path), e);
    }
  }

  private static class CleanFilter implements DirectoryStream.Filter<Path> {
    @Override
    public boolean accept(Path path) throws IOException {
      if (!Files.isDirectory(path)) {
        return false;
      }

      if (!path.getFileName().toString().startsWith(TMP_NAME_PREFIX)) {
        return false;
      }

      long threshold = System.currentTimeMillis() - CLEAN_MAX_AGE;

      // we could also check the timestamp in the name, instead
      BasicFileAttributes attrs;

      try {
        attrs = Files.readAttributes(path, BasicFileAttributes.class);
      } catch (IOException ioe) {
        LOG.error(String.format("Couldn't read file attributes for %s : ", path), ioe);
        return false;
      }

      long creationTime = attrs.creationTime().toMillis();
      return creationTime < threshold;
    }
  }

  @Override
  public void start(PicoContainer container) {
    started = true;
  }

  @Override
  public void stop(PicoContainer container) {
    if (tempFolder != null) {
      tempFolder.stop();
    }
  }

  @Override
  public void dispose(PicoContainer container) {
    // nothing to do
  }

  @Override
  public boolean componentHasLifecycle() {
    return true;
  }

  @Override
  public boolean isStarted() {
    return started;
  }
}
