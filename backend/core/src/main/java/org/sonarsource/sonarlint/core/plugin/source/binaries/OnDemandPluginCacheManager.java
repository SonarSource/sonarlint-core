/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin.source.binaries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Manages cleanup of old plugin versions from the cache.
 * Deletes version directories not modified within the last 60 days, skipping the current version.
 */
public class OnDemandPluginCacheManager {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final long RETENTION_DAYS = 60;

  /**
   * Cleans up old plugin versions from the cache directory.
   *
   * @param cacheDirectory the base cache directory (e.g., {storageRoot}/cache/ondemand-plugins/cpp)
   * @param currentVersion the current version to keep (not deleted)
   */
  void cleanupOldVersions(Path cacheDirectory, String currentVersion) {
    if (!Files.isDirectory(cacheDirectory)) {
      return;
    }

    var cutoffTime = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

    try (var stream = Files.list(cacheDirectory)) {
      stream.filter(Files::isDirectory)
        .filter(versionDir -> !versionDir.getFileName().toString().equals(currentVersion))
        .filter(versionDir -> isOlderThan(versionDir, cutoffTime))
        .forEach(OnDemandPluginCacheManager::deleteVersionDirectory);
    } catch (IOException e) {
      LOG.debug("Error cleaning up old plugin versions", e);
    }
  }

  private static boolean isOlderThan(Path directory, Instant cutoffTime) {
    try {
      var lastModified = Files.getLastModifiedTime(directory).toInstant();
      return lastModified.isBefore(cutoffTime);
    } catch (IOException e) {
      LOG.debug("Failed to read last-modified time for plugin cache directory: {}", directory, e);
      return false;
    }
  }

  private static void deleteVersionDirectory(Path directory) {
    try {
      FileUtils.deleteDirectory(directory.toFile());
      LOG.debug("Deleted old plugin version: {}", directory.getFileName());
    } catch (Exception e) {
      LOG.debug("Failed to delete old version directory: {}", directory, e);
    }
  }

}
