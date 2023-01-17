/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.springframework.context.annotation.Bean;

public class GlobalTempFolderProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final long CLEAN_MAX_AGE = TimeUnit.DAYS.toMillis(21);
  private static final String TMP_NAME_PREFIX = ".sonarlinttmp_";

  private GlobalTempFolder tempFolder;

  @Bean("GlobalTempFolder")
  public GlobalTempFolder provide(AnalysisEngineConfiguration globalConfiguration) {
    if (tempFolder == null) {
      tempFolder = cleanAndCreateTempFolder(globalConfiguration.getWorkDir());
    }
    return tempFolder;
  }

  private static GlobalTempFolder cleanAndCreateTempFolder(Path workingPath) {
    try {
      cleanTempFolders(workingPath);
    } catch (IOException e) {
      LOG.error(String.format("failed to clean global working directory: %s", workingPath), e);
    }
    var tempDir = createTempFolder(workingPath);
    return new GlobalTempFolder(tempDir.toFile(), true);
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
      try (var stream = Files.newDirectoryStream(path, new CleanFilter())) {
        for (Path p : stream) {
          FileUtils.deleteQuietly(p.toFile());
        }
      }
    }
  }

  private static class CleanFilter implements DirectoryStream.Filter<Path> {
    @Override
    public boolean accept(Path path) throws IOException {
      if (!Files.isDirectory(path) || !path.getFileName().toString().startsWith(TMP_NAME_PREFIX)) {
        return false;
      }

      var threshold = System.currentTimeMillis() - CLEAN_MAX_AGE;

      // we could also check the timestamp in the name, instead
      BasicFileAttributes attrs;

      try {
        attrs = Files.readAttributes(path, BasicFileAttributes.class);
      } catch (IOException ioe) {
        LOG.error(String.format("Couldn't read file attributes for %s : ", path), ioe);
        return false;
      }

      var creationTime = attrs.creationTime().toMillis();
      return creationTime < threshold;
    }
  }

}
