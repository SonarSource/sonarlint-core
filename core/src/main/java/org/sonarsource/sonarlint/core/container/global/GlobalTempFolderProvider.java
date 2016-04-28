/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.global;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.picocontainer.ComponentLifecycle;
import org.picocontainer.PicoContainer;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.internal.DefaultTempFolder;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;

public class GlobalTempFolderProvider extends ProviderAdapter implements ComponentLifecycle<TempFolder> {
  private static final Logger LOG = Loggers.get(GlobalTempFolderProvider.class);
  private static final long CLEAN_MAX_AGE = TimeUnit.DAYS.toMillis(21);
  static final String TMP_NAME_PREFIX = ".sonartmp_";
  private boolean started = false;

  private DefaultTempFolder tempFolder;

  public TempFolder provide(AbstractGlobalConfiguration globalConfiguration) {
    if (tempFolder == null) {

      Path workingPath = globalConfiguration.getWorkDir();
      try {
        cleanTempFolders(workingPath);
      } catch (IOException e) {
        LOG.error(String.format("failed to clean global working directory: %s", workingPath), e);
      }
      Path tempDir = createTempFolder(workingPath);
      tempFolder = new DefaultTempFolder(tempDir.toFile(), true);
    }
    return tempFolder;
  }

  private static Path createTempFolder(Path workingPath) {
    try {
      Files.createDirectories(workingPath);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create working path: " + workingPath, e);
    }

    try {
      return Files.createTempDirectory(workingPath, TMP_NAME_PREFIX);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create temporary folder in " + workingPath, e);
    }
  }

  private static void cleanTempFolders(Path path) throws IOException {
    if (Files.exists(path)) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, new CleanFilter())) {
        for (Path p : stream) {
          FileUtils.deleteQuietly(p.toFile());
        }
      }
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
