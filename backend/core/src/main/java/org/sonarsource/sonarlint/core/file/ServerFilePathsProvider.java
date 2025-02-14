/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.file;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;

public class ServerFilePathsProvider {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConnectionManager connectionManager;
  private final Map<Binding, Path> cachedResponseFilePathByBinding = new HashMap<>();
  private final Path cacheDirectoryPath;
  private final Cache<Binding, List<Path>> temporaryInMemoryFilePathCacheByBinding;

  public ServerFilePathsProvider(ConnectionManager connectionManager, UserPaths userPaths) {
    this.connectionManager = connectionManager;
    this.cacheDirectoryPath = userPaths.getStorageRoot().resolve("cache");
    this.temporaryInMemoryFilePathCacheByBinding = CacheBuilder.newBuilder()
      .expireAfterWrite(Duration.of(1, ChronoUnit.MINUTES))
      .maximumSize(3)
      .build();

    clearCachePath();
  }

  private void clearCachePath() {
    if (!cacheDirectoryPath.toFile().exists()) {
      return;
    }
    try {
      FileUtils.deleteDirectory(cacheDirectoryPath.toFile());
    } catch (IOException e) {
      LOG.debug("Error occurred while deleting a cache file", e);
    }
  }

  Optional<List<Path>> getServerPaths(Binding binding, SonarLintCancelMonitor cancelMonitor) {
    return getPathsFromInMemoryCache(binding)
      .or(() -> getPathsFromFileCache(binding))
      .or(() -> fetchPathsFromServer(binding, cancelMonitor));
  }

  private Optional<List<Path>> getPathsFromInMemoryCache(Binding binding) {
    return Optional.ofNullable(temporaryInMemoryFilePathCacheByBinding.getIfPresent(binding));
  }

  private Optional<List<Path>> getPathsFromFileCache(Binding binding) {
    return Optional.ofNullable(cachedResponseFilePathByBinding.get(binding))
      .filter(path -> path.toFile().exists())
      .map(path -> {
        List<Path> paths = readServerPathsFromFile(path);
        putToInMemoryCache(binding, paths);
        return paths;
      });
  }

  private Optional<List<Path>> fetchPathsFromServer(Binding binding, SonarLintCancelMonitor cancelMonitor) {
    var connectionOpt = connectionManager.tryGetConnection(binding.connectionId());
    if (connectionOpt.isEmpty()) {
      LOG.debug("Connection '{}' does not exist", binding.connectionId());
      return Optional.empty();
    }
    try {
      return connectionManager.withValidConnectionFlatMapOptionalAndReturn(binding.connectionId(), serverApi -> {
        List<Path> paths = fetchPathsFromServer(serverApi, binding.sonarProjectKey(), cancelMonitor);
        cacheServerPaths(binding, paths);
        return Optional.of(paths);
      });
    } catch (CancellationException e) {
      throw e;
    } catch (Exception e) {
      LOG.debug("Error while getting server file paths for project '{}'", binding.sonarProjectKey(), e);
      return Optional.empty();
    }
  }

  private static List<Path> readServerPathsFromFile(Path responsePath) {
    try {
      return Files.readAllLines(responsePath).stream().map(Paths::get).toList();
    } catch (IOException e) {
      LOG.debug("Error occurred while reading the file server path response file cache {}", responsePath);
      return Collections.emptyList();
    }
  }

  private void putToInMemoryCache(Binding binding, List<Path> paths) {
    temporaryInMemoryFilePathCacheByBinding.put(binding, paths);
  }

  private static List<Path> fetchPathsFromServer(ServerApi serverApi, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    return serverApi.component().getAllFileKeys(projectKey, cancelMonitor).stream()
      .map(fileKey -> StringUtils.substringAfterLast(fileKey, ":"))
      .map(Paths::get)
      .toList();
  }

  private void cacheServerPaths(Binding binding, List<Path> paths) {
    var fileName = UUID.randomUUID().toString();
    var filePath = cacheDirectoryPath.resolve(fileName);
    try {
      Files.createDirectories(cacheDirectoryPath);
      writeToFile(filePath, paths);
      cachedResponseFilePathByBinding.put(binding, filePath);
      putToInMemoryCache(binding, paths);
    } catch (IOException e) {
      LOG.debug("Error occurred while writing the cache file", e);
    }
  }

  private static void writeToFile(Path filePath, List<Path> paths) throws IOException {
    try (var bufferedWriter = new BufferedWriter(new FileWriter(filePath.toFile(), Charset.defaultCharset()))) {
      for (Path path : paths) {
        bufferedWriter.write(path + System.lineSeparator());
      }
    }
  }

  @VisibleForTesting
  void clearInMemoryCache() {
    temporaryInMemoryFilePathCacheByBinding.invalidateAll();
  }
}
