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
package org.sonarsource.sonarlint.core.plugin.source.server;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactStorageCleaner;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;

public class ServerArtifactStorageCleaner implements ArtifactStorageCleaner {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final String connectionId;
  private final StorageService storageService;
  private final ServerPluginsCache serverPluginsCache;

  public ServerArtifactStorageCleaner(String connectionId, StorageService storageService, ServerPluginsCache serverPluginsCache) {
    this.connectionId = connectionId;
    this.storageService = storageService;
    this.serverPluginsCache = serverPluginsCache;
  }

  @Override
  public void clean(Set<String> selectedArtifactKeys) {
    try {
      var serverPluginsByKey = serverPluginsCache.getPlugins(connectionId).orElse(List.of()).stream()
        .collect(Collectors.toMap(ServerPlugin::getKey, Function.identity()));
      var storedPlugins = storageService.connection(connectionId).plugins().getStoredPluginsByKey();
      var expectedPlugins = selectedArtifactKeys.stream()
        .filter(serverPluginsByKey::containsKey)
        .map(key -> {
          var serverPlugin = serverPluginsByKey.get(key);
          var stored = storedPlugins.get(key);
          if (stored != null && Files.exists(stored.getJarPath()) && stored.hasSameHash(serverPlugin)) {
            return new ServerPlugin(key, serverPlugin.getHash(), stored.getJarPath().getFileName().toString(), serverPlugin.isSonarLintSupported());
          }
          return serverPlugin;
        })
        .toList();
      storageService.connection(connectionId).plugins().cleanUpUnknownPlugins(expectedPlugins);
    } catch (Exception e) {
      LOG.debug("Could not clean server plugin storage for connection '{}'", connectionId);
    }
  }
}
