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
package org.sonarsource.sonarlint.core.plugin.cache;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.annotation.CheckForNull;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

/**
 * This class is responsible for managing Sonar batch file cache. You can put file into cache and
 * later try to retrieve them. MD5 is used to differentiate files (name is not secure as files may come
 * from different Sonar servers and have same name but be actually different, and same for SNAPSHOTs).
 */
public class PluginCache {

  private static final Logger LOG = Loggers.get(PluginCache.class);

  private final Path cacheDir;
  private final Path tmpDirInCacheDir;
  private final PluginHashes hashes;

  PluginCache(Path cacheDir, PluginHashes fileHashes) {
    this.hashes = fileHashes;
    createDirIfNeeded(cacheDir, "user cache");
    this.cacheDir = cacheDir;
    LOG.debug("Plugin cache: {}", cacheDir.toString());
    this.tmpDirInCacheDir = cacheDir.resolve("_tmp");
    createDirIfNeeded(this.tmpDirInCacheDir, "temp dir");
  }

  public static PluginCache create(Path cachePath) {
    return new PluginCache(cachePath, new PluginHashes());
  }

  public Path getCacheDir() {
    return cacheDir;
  }

  /**
   * Look for a file in the cache by its filename and md5 checksum. If the file is not
   * present then return null.
   */
  @CheckForNull
  public Path get(String filename, String hash) {
    Path cachedFile = cacheDir.resolve(hash).resolve(filename);
    if (Files.exists(cachedFile)) {
      return cachedFile;
    }
    LOG.debug("No file found in the cache with name {} and hash {}", filename, hash);
    return null;
  }

  @FunctionalInterface
  public interface Copier {
    void copy(String filename, Path toFile) throws IOException;
  }

  public Path get(String filename, String hash, Copier copier) {
    // Does not fail if another process tries to create the directory at the same time.
    Path hashDir = hashDir(hash);
    Path targetFile = hashDir.resolve(filename);
    if (Files.notExists(targetFile)) {
      Path tempFile = newTempFile();
      copy(copier, filename, tempFile);
      String downloadedHash = hashes.of(tempFile);
      if (!hash.equals(downloadedHash)) {
        throw new IllegalStateException("INVALID HASH: File " + tempFile + " was expected to have hash " + hash
          + " but was copied with hash " + downloadedHash);
      }
      createDirIfNeeded(hashDir, "target directory in cache");
      renameQuietly(tempFile, targetFile);
    }
    return targetFile;
  }

  private static void copy(Copier copier, String filename, Path tempFile) {
    try {
      copier.copy(filename, tempFile);
    } catch (IOException e) {
      throw new IllegalStateException("Fail to copy " + filename + " to " + tempFile, e);
    }
  }

  private static void renameQuietly(Path sourceFile, Path targetFile) {
    try {
      rename(sourceFile, targetFile);
    } catch (Exception e) {
      // Check if the file was cached by another process during copy
      if (Files.notExists(targetFile)) {
        throw new IllegalStateException("Fail to move " + sourceFile + " to " + targetFile, e);
      }
    }
  }

  private static void rename(Path sourceFile, Path targetFile) throws IOException {
    try {
      Files.move(sourceFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      LOG.warn("Atomic rename from '{}' to '{}' not supported", sourceFile, targetFile);
      Files.move(sourceFile, targetFile);
    }
  }

  private Path hashDir(String hash) {
    return cacheDir.resolve(hash);
  }

  private Path newTempFile() {
    try {
      return Files.createTempFile(tmpDirInCacheDir, null, null);
    } catch (IOException e) {
      throw new IllegalStateException("Fail to create temp file in " + tmpDirInCacheDir, e);
    }
  }

  public Path createTempDir() {
    try {
      return Files.createTempDirectory(tmpDirInCacheDir, null);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create directory in " + tmpDirInCacheDir, e);
    }
  }

  private static void createDirIfNeeded(Path dir, String debugTitle) {
    LOG.debug("Create : {}", dir);
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create " + debugTitle + dir, e);
    }
  }
}
