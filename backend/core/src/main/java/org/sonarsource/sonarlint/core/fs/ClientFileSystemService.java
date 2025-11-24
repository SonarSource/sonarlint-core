/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.fs;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.commons.SmartCancelableLoadingCache;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.GetBaseDirParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.GetClientFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.GetFileByIdePathParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.GetFilesByPatternParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListDirectoriesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.WarmUpFileSystemCacheParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

public class ClientFileSystemService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintRpcClient rpcClient;
  private final ApplicationEventPublisher eventPublisher;
  private final Map<URI, ClientFile> filesByUri = new ConcurrentHashMap<>();
  private final Map<String, Path> baseDirPerConfigScopeId = new ConcurrentHashMap<>();
  private final OpenFilesRepository openFilesRepository;
  private final TelemetryService telemetryService;
  private final SmartCancelableLoadingCache<String, Map<URI, ClientFile>> filesByConfigScopeIdCache =
    new SmartCancelableLoadingCache<>("sonarlint-filesystem", this::initializeFileSystem);

  public ClientFileSystemService(SonarLintRpcClient rpcClient, ApplicationEventPublisher eventPublisher, OpenFilesRepository openFilesRepository,
    TelemetryService telemetryService) {
    this.rpcClient = rpcClient;
    this.eventPublisher = eventPublisher;
    this.openFilesRepository = openFilesRepository;
    this.telemetryService = telemetryService;
  }

  public List<ClientFile> getFiles(String configScopeId) {
    return List.copyOf(filesByConfigScopeIdCache.get(configScopeId).values());
  }

  private static ClientFile fromDto(ClientFileDto clientFileDto) {
    var charset = charsetFromDto(clientFileDto.getCharset());
    var forcedLanguage = clientFileDto.getDetectedLanguage();
    var forcedSonarLanguage = forcedLanguage == null ? null : SonarLanguage.valueOf(forcedLanguage.name());
    var file = new ClientFile(clientFileDto.getUri(), clientFileDto.getConfigScopeId(),
      clientFileDto.getIdeRelativePath(),
      clientFileDto.isTest(),
      charset,
      clientFileDto.getFsPath(),
      forcedSonarLanguage,
      clientFileDto.isUserDefined());
    if (clientFileDto.getContent() != null) {
      file.setDirty(clientFileDto.getContent());
    }
    return file;
  }

  @Nullable
  private static Charset charsetFromDto(@Nullable String dtoCharset) {
    if (dtoCharset == null) {
      return null;
    }
    try {
      return Charset.forName(dtoCharset);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * @deprecated Use getFileByIdePath for specific files instead. This method will try lazy loading first.
   */
  @Deprecated
  public List<ClientFile> findFilesByNamesInScope(String configScopeId, List<String> filenames) {
    // Try lazy loading first
    var result = new ArrayList<ClientFile>();
    for (String filename : filenames) {
      var file = getFileByIdePath(configScopeId, Path.of(filename));
      if (file != null) {
        result.add(file);
      }
    }
    // Fall back to cached files if lazy loading didn't find anything
    if (result.isEmpty()) {
      return getFiles(configScopeId).stream()
        .filter(f -> filenames.contains(f.getClientRelativePath().getFileName().toString()))
        .toList();
    }
    return result;
  }

  /**
   * @deprecated Use getFilesByPattern for .sonarlint/**\/*.json instead. This method will try lazy loading first.
   */
  @Deprecated
  public List<ClientFile> findSonarlintConfigurationFilesByScope(String configScopeId) {
    // Try lazy loading first with glob pattern for .sonarlint/**/*.json
    var lazyFiles = getFilesByPattern(configScopeId, ".sonarlint/**/*.json");
    if (!lazyFiles.isEmpty()) {
      return lazyFiles;
    }
    // Fall back to cached files
    return getFiles(configScopeId).stream()
      .filter(ClientFile::isSonarlintConfigurationFile)
      .toList();
  }

  public Map<URI, ClientFile> initializeFileSystem(String configScopeId, SonarLintCancelMonitor cancelMonitor) {
    var result = new ConcurrentHashMap<URI, ClientFile>();
    var files = getClientFileDtos(configScopeId, cancelMonitor);
    files.forEach(clientFileDto -> {
      var clientFile = fromDto(clientFileDto);
      filesByUri.put(clientFileDto.getUri(), clientFile);
      result.put(clientFileDto.getUri(), clientFile);
    });
    return result;
  }

  private List<ClientFileDto> getClientFileDtos(String configScopeId, SonarLintCancelMonitor cancelMonitor) {
    var startTime = System.currentTimeMillis();
    var future = rpcClient.listFiles(new ListFilesParams(configScopeId));
    cancelMonitor.onCancel(() -> future.cancel(true));
    var files = future.join().getFiles();
    var endTime = System.currentTimeMillis();
    telemetryService.updateListFilesPerformance(files.size(), endTime - startTime);
    return files;
  }

  public void didUpdateFileSystem(DidUpdateFileSystemParams params) {
    var removed = new ArrayList<ClientFile>();
    params.getRemovedFiles().forEach(uri -> {
      var clientFile = filesByUri.remove(uri);
      if (clientFile != null) {
        filesByConfigScopeIdCache.get(clientFile.getConfigScopeId()).remove(uri);
        removed.add(clientFile);
      }
    });

    var added = new ArrayList<ClientFile>();
    params.getAddedFiles().forEach(clientFileDto -> {
      var clientFile = fromDto(clientFileDto);
      var previousFile = filesByUri.put(clientFileDto.getUri(), clientFile);
      // We only send send the ADDED event for files that were actually added (not existing before)
      if (previousFile == null) {
        added.add(clientFile);
      }
      var byScope = filesByConfigScopeIdCache.get(clientFileDto.getConfigScopeId());
      byScope.put(clientFileDto.getUri(), clientFile);
    });

    var updated = new ArrayList<ClientFile>();
    params.getChangedFiles().forEach(clientFileDto -> {
      var clientFile = fromDto(clientFileDto);
      var previousFile = filesByUri.put(clientFileDto.getUri(), clientFile);
      // Modifying an unknown file is equals to adding it
      if (previousFile != null) {
        updated.add(clientFile);
      } else {
        added.add(clientFile);
      }
      var byScope = filesByConfigScopeIdCache.get(clientFileDto.getConfigScopeId());
      byScope.put(clientFileDto.getUri(), clientFile);
    });

    eventPublisher.publishEvent(new FileSystemUpdatedEvent(removed, added, updated));
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    var removedFilesByURI = filesByConfigScopeIdCache.get(event.getRemovedConfigurationScopeId());
    filesByConfigScopeIdCache.clear(event.getRemovedConfigurationScopeId());
    if (removedFilesByURI != null) {
      removedFilesByURI.keySet().forEach(filesByUri::remove);
    }
  }

  @PreDestroy
  public void shutdown() {
    filesByConfigScopeIdCache.close();
  }

  /**
   * This will trigger loading the FS from the client if needed
   */
  @CheckForNull
  public ClientFile getClientFiles(String configScopeId, URI fileUri) {
    return filesByConfigScopeIdCache.get(configScopeId).get(fileUri);
  }

  /**
   * This will NOT trigger loading the FS from the client
   */
  @CheckForNull
  public ClientFile getClientFile(URI fileUri) {
    return filesByUri.get(fileUri);
  }

  @CheckForNull
  public Path getBaseDir(String configurationScopeId) {
    return baseDirPerConfigScopeId.computeIfAbsent(configurationScopeId, k -> {
      try {
        return rpcClient.getBaseDir(new GetBaseDirParams(configurationScopeId)).join().getBaseDir();
      } catch (Exception e) {
        LOG.error("Error when getting the base dir from the client", e);
        return null;
      }
    });
  }

  public void didOpenFile(String configurationScopeId, URI fileUri) {
    var isNewlyOpenedFile = openFilesRepository.considerOpened(configurationScopeId, fileUri);
    if (isNewlyOpenedFile) {
      eventPublisher.publishEvent(new FileOpenedEvent(configurationScopeId, fileUri));
    }
  }

  public void didCloseFile(String configurationScopeId, URI fileUri) {
    openFilesRepository.considerClosed(configurationScopeId, fileUri);
  }

  public Map<String, Set<URI>> groupFilesByConfigScope(Set<URI> fileUris) {
    return fileUris.stream()
      .map(filesByUri::get)
      .filter(Objects::nonNull)
      .collect(Collectors.groupingBy(
        ClientFile::getConfigScopeId,
        Collectors.mapping(
          ClientFile::getUri,
          Collectors.toSet()
        )
      ));
  }

  /**
   * Get files matching a glob pattern using lazy loading.
   * Tries the new RPC method first, falls back to cache.
   * @since 10.12
   */
  public List<ClientFile> getFilesByPattern(String configScopeId, String globPattern) {
    try {
      var response = rpcClient.getFilesByPattern(new GetFilesByPatternParams(configScopeId, globPattern)).join();
      var files = response.getFiles();
      if (!files.isEmpty()) {
        // Cache the files we just loaded
        addFilesToVirtualFileSystem(configScopeId, files);
        return files.stream().map(ClientFileSystemService::fromDto).toList();
      }
    } catch (Exception e) {
      try {
        LOG.debug("Error getting files by pattern, falling back to cache", e);
      } catch (Exception ignored) {
        // Logging might not be configured yet during initialization
      }
    }
    // Fall back to cache
    return getFiles(configScopeId).stream()
      .filter(f -> f.getClientRelativePath().toString().matches(globToRegex(globPattern)))
      .toList();
  }

  private void addFilesToVirtualFileSystem(String configScopeId, List<ClientFileDto> files) {
    files.forEach(dto -> {
      var clientFile = fromDto(dto);
      filesByUri.put(dto.getUri(), clientFile);
      var byScope = filesByConfigScopeIdCache.get(configScopeId);
      if (byScope != null) {
        byScope.put(dto.getUri(), clientFile);
      }
    });
  }

  /**
   * Get a file by its IDE path using lazy loading.
   * Tries the new RPC method first, falls back to cache.
   * @since 10.12
   */
  @CheckForNull
  public ClientFile getFileByIdePath(String configScopeId, Path filePath) {
    try {
      var response = rpcClient.getFileByIdePath(new GetFileByIdePathParams(configScopeId, filePath)).join();
      var fileDto = response.getFile();
      if (fileDto != null) {
        var clientFile = fromDto(fileDto);
        // Cache the file we just loaded
        filesByUri.put(fileDto.getUri(), clientFile);
        var byScope = filesByConfigScopeIdCache.get(configScopeId);
        if (byScope != null) {
          byScope.put(fileDto.getUri(), clientFile);
        }
        return clientFile;
      }
    } catch (Exception e) {
      try {
        LOG.debug("Error getting file by IDE path, falling back to cache", e);
      } catch (Exception ignored) {
        // Logging might not be configured yet during initialization
      }
    }
    // Fall back to cache
    return getFiles(configScopeId).stream()
      .filter(f -> f.getClientRelativePath().equals(filePath))
      .findFirst()
      .orElse(null);
  }

  /**
   * Check if a file exists using lazy loading.
   * Tries the new RPC method first, falls back to cache.
   * @since 10.12
   */
  public boolean doesFileExist(URI fileUri) {
    // First check cache
    if (filesByUri.containsKey(fileUri)) {
      return true;
    }
    // Try lazy loading
    try {
      var response = rpcClient.getClientFile(new GetClientFileParams(fileUri)).join();
      var fileDto = response.getFile();
      if (fileDto != null) {
        var clientFile = fromDto(fileDto);
        // Cache the file we just loaded
        filesByUri.put(fileDto.getUri(), clientFile);
        var byScope = filesByConfigScopeIdCache.get(clientFile.getConfigScopeId());
        if (byScope != null) {
          byScope.put(fileDto.getUri(), clientFile);
        }
        return true;
      }
    } catch (Exception e) {
      try {
        LOG.debug("Error checking file existence", e);
      } catch (Exception ignored) {
        // Logging might not be configured yet during initialization
      }
    }
    return false;
  }

  /**
   * Get directories (not files) for a configuration scope using lazy loading.
   * Tries the new RPC method first, falls back to extracting directories from cached files.
   * @since 10.12
   */
  public List<Path> getDirectories(String configScopeId) {
    try {
      var response = rpcClient.listDirectories(new ListDirectoriesParams(configScopeId)).join();
      var directories = response.getDirectories();
      if (!directories.isEmpty()) {
        return directories.stream()
          .map(dto -> fromDto(dto).getClientRelativePath())
          .toList();
      }
    } catch (Exception e) {
      try {
        LOG.debug("Error getting directories, falling back to cache", e);
      } catch (Exception ignored) {
        // Logging might not be configured yet during initialization
      }
    }
    // Fall back to extracting directories from cached files
    return getFiles(configScopeId).stream()
      .map(ClientFile::getClientRelativePath)
      .map(Path::getParent)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
  }

  /**
   * Listen for configuration scopes added and trigger cache warmup.
   * @since 10.12
   */
  @EventListener
  public void onConfigurationScopesAdded(ConfigurationScopesAddedWithBindingEvent event) {
    var configScopeIds = event.getConfigScopeIds();
    if (!configScopeIds.isEmpty()) {
      try {
        LOG.debug("Triggering cache warmup for {} newly added configuration scopes", configScopeIds.size());
      } catch (Exception ignored) {
        // Logging might not be configured yet during initialization
      }
      requestCacheWarmup(configScopeIds);
    }
  }

  /**
   * Request asynchronous cache warmup for the given configuration scopes.
   * @since 10.12
   */
  public void requestCacheWarmup(Set<String> configScopeIds) {
    try {
      rpcClient.warmUpFileSystemCache(new WarmUpFileSystemCacheParams(configScopeIds));
      try {
        LOG.debug("Requested cache warmup for {} configuration scopes", configScopeIds.size());
      } catch (Exception ignored) {
        // Logging might not be configured yet during initialization
      }
    } catch (Exception e) {
      try {
        LOG.debug("Error requesting cache warmup", e);
      } catch (Exception ignored) {
        // Logging might not be configured yet during initialization
      }
    }
  }

  /**
   * Submit a chunk of files to the cache during warmup.
   * @since 10.12
   */
  public void submitFileCacheChunk(String configScopeId, List<ClientFileDto> files) {
    try {
      LOG.debug("Received cache chunk with {} files for config scope '{}'", files.size(), configScopeId);
    } catch (Exception ignored) {
      // Logging might not be configured yet during initialization
    }
    addFilesToVirtualFileSystem(configScopeId, files);
  }

  /**
   * Simple glob to regex conversion for pattern matching.
   */
  private String globToRegex(String glob) {
    return glob.replace(".", "\\.")
      .replace("*", ".*")
      .replace("?", ".");
  }

}
