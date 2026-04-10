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
package org.sonarsource.sonarlint.core.plugin.loading.strategy;

import java.util.concurrent.ConcurrentHashMap;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginsCache;
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginSource;
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginDownloader;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.storage.StorageService;

/**
 * Creates and caches {@link ConnectedArtifactsLoadingStrategy} instances, one per connection ID.
 *
 * <p>The cache ensures that the same strategy — and its underlying
 * {@link ServerPluginSource} — is reused across calls for the same connection, which is required
 * for consistent in-progress download tracking. Call {@link #evict(String)} when a connection is
 * removed.</p>
 */
public class ConnectedArtifactsLoadingStrategyFactory {

  private final ConcurrentHashMap<String, ConnectedArtifactsLoadingStrategy> cache = new ConcurrentHashMap<>();

  private final InitializeParams params;
  private final BinariesArtifactSource binariesSource;
  private final StorageService storageService;
  private final ServerPluginsCache serverPluginsCache;
  private final ServerPluginDownloader downloader;
  private final LanguageSupportRepository languageSupportRepository;

  public ConnectedArtifactsLoadingStrategyFactory(InitializeParams params,
    BinariesArtifactSource binariesSource,
    StorageService storageService,
    ServerPluginsCache serverPluginsCache,
    ServerPluginDownloader downloader,
    LanguageSupportRepository languageSupportRepository) {
    this.params = params;
    this.binariesSource = binariesSource;
    this.storageService = storageService;
    this.serverPluginsCache = serverPluginsCache;
    this.downloader = downloader;
    this.languageSupportRepository = languageSupportRepository;
  }

  public ConnectedArtifactsLoadingStrategy getOrCreate(String connectionId) {
    return cache.computeIfAbsent(connectionId, id -> {
      var serverSource = new ServerPluginSource(id, storageService, serverPluginsCache, downloader);
      return new ConnectedArtifactsLoadingStrategy(params, binariesSource, serverSource, languageSupportRepository);
    });
  }

  public void evict(String connectionId) {
    cache.remove(connectionId);
  }
}
