/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.SmartCancelableLoadingCache;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
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

  private final SmartCancelableLoadingCache<String, Map<URI, ClientFile>> filesByConfigScopeIdCache =
    new SmartCancelableLoadingCache<>("sonarlint-filesystem", this::initializeFileSystem);

  public ClientFileSystemService(SonarLintRpcClient rpcClient, ApplicationEventPublisher eventPublisher) {
    this.rpcClient = rpcClient;
    this.eventPublisher = eventPublisher;
  }

  public List<ClientFile> getFiles(String configScopeId) {
    return List.copyOf(filesByConfigScopeIdCache.get(configScopeId).values());
  }

  private static ClientFile fromDto(ClientFileDto clientFileDto) {
    var dtoCharset = clientFileDto.getCharset();
    var charset = dtoCharset != null ? Charset.forName(dtoCharset) : null;
    var file = new ClientFile(clientFileDto.getUri(), clientFileDto.getConfigScopeId(),
      clientFileDto.getIdeRelativePath(),
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

  public Map<URI, ClientFile> initializeFileSystem(String configScopeId, SonarLintCancelMonitor cancelMonitor) {
    var result = new ConcurrentHashMap<URI, ClientFile>();
    var future = rpcClient.listFiles(new ListFilesParams(configScopeId));
    cancelMonitor.onCancel(() -> future.cancel(true));
    future.join().getFiles().forEach(clientFileDto -> {
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
        filesByConfigScopeIdCache.get(clientFile.getConfigScopeId()).remove(uri);
        removed.add(clientFile);
      }
    });
    var addedOrUpdated = new ArrayList<ClientFile>();
    params.getAddedOrChangedFiles().forEach(clientFileDto -> {
      var clientFile = fromDto(clientFileDto);
      filesByUri.put(clientFileDto.getUri(), clientFile);
      var byScope = filesByConfigScopeIdCache.get(clientFileDto.getConfigScopeId());
      byScope.put(clientFileDto.getUri(), clientFile);
      addedOrUpdated.add(clientFile);
    });
    eventPublisher.publishEvent(new FileSystemUpdatedEvent(removed, addedOrUpdated));
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
}
