/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.fs;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import static java.util.stream.Collectors.toList;

@Named
@Singleton
public class ClientFileSystemService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintRpcClient rpcClient;
  private final ApplicationEventPublisher eventPublisher;
  private final Map<URI, ClientFile> filesByUri = new ConcurrentHashMap<>();

  private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-filesystem"));

  private final AsyncLoadingCache<String, Map<URI, ClientFile>> filesByConfigScopeId = Caffeine.newBuilder()
    .executor(executorService)
    .buildAsync(this::initializeFileSystem);

  public ClientFileSystemService(SonarLintRpcClient rpcClient, ApplicationEventPublisher eventPublisher) {
    this.rpcClient = rpcClient;
    this.eventPublisher = eventPublisher;
  }

  public List<ClientFile> getFiles(String configScopeId) {
    return List.copyOf(filesByConfigScopeId.get(configScopeId).join().values());
  }

  private static ClientFile fromDto(ClientFileDto clientFileDto) {
    var dtoCharset = clientFileDto.getCharset();
    var charset = dtoCharset != null ? Charset.forName(dtoCharset) : null;
    var file = new ClientFile(clientFileDto.getUri(), clientFileDto.getConfigScopeId(),
      clientFileDto.getRelativePath(),
      clientFileDto.isTest(),
      charset,
      clientFileDto.getFsPath());
    if (clientFileDto.getContent() != null) {
      file.setDirty(clientFileDto.getContent());
    }
    return file;
  }

  public List<ClientFile> findFileByNamesInScope(String configScopeId, List<String> filenames) {
    return getFiles(configScopeId).stream()
      .filter(f -> filenames.contains(f.getClientRelativePath().getFileName().toString()))
      .collect(toList());
  }

  public Map<URI, ClientFile> initializeFileSystem(String configScopeId) {
    var result = new ConcurrentHashMap<URI, ClientFile>();
    var response = rpcClient.listFiles(new ListFilesParams(configScopeId)).join();
    response.getFiles().forEach(clientFileDto -> {
      var clientFile = fromDto(clientFileDto);
      filesByUri.put(clientFileDto.getUri(), clientFile);
      result.put(clientFileDto.getUri(), clientFile);
    });
    return result;
  }

  public void didUpdateFileSystem(DidUpdateFileSystemParams params) {
    var removed = new ArrayList<ClientFile>();
    params.getRemovedFiles().forEach(uri -> {
      var clientFile = filesByUri.remove(uri);
      if (clientFile != null) {
        filesByConfigScopeId.synchronous().get(clientFile.getConfigScopeId()).remove(uri);
        removed.add(clientFile);
      }
    });
    var addedOrUpdated = new ArrayList<ClientFile>();
    params.getAddedOrChangedFiles().forEach(clientFileDto -> {
      var clientFile = fromDto(clientFileDto);
      filesByUri.put(clientFileDto.getUri(), clientFile);
      var byScope = filesByConfigScopeId.synchronous().getIfPresent(clientFileDto.getConfigScopeId());
      if (byScope == null) {
        filesByConfigScopeId.synchronous().put(clientFileDto.getConfigScopeId(), new ConcurrentHashMap<>());
      }
      filesByConfigScopeId.synchronous().get(clientFileDto.getConfigScopeId()).put(clientFileDto.getUri(), clientFile);
      addedOrUpdated.add(clientFile);
    });
    eventPublisher.publishEvent(new FileSystemUpdatedEvent(removed, addedOrUpdated));
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    var removedFilesByURI = filesByConfigScopeId.synchronous().get(event.getRemovedConfigurationScopeId());
    filesByConfigScopeId.synchronous().invalidate(event.getRemovedConfigurationScopeId());
    if (removedFilesByURI != null) {
      removedFilesByURI.keySet().forEach(filesByUri::remove);
    }
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop filesystem executor service in a timely manner");
    }
  }

  /**
   * This will trigger loading the FS from the client if needed
   */
  @CheckForNull
  public ClientFile getClientFiles(String configScopeId, URI fileUri) {
    return filesByConfigScopeId.get(configScopeId).join().get(fileUri);
  }

  /**
   * This will NOT trigger loading the FS from the client
   */
  @CheckForNull
  public ClientFile getClientFile(URI fileUri) {
    return filesByUri.get(fileUri);
  }
}
