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

import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;

/**
 * Performs blocking downloads of server plugins. Asynchronous execution and status updates are
 * owned by the central artifact download coordinator.
 */
public class ServerPluginDownloader {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final StorageService storageService;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;

  public ServerPluginDownloader(StorageService storageService, SonarQubeClientManager sonarQubeClientManager,
    ConnectionConfigurationRepository connectionConfigurationRepository) {
    this.storageService = storageService;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
  }

  void downloadPluginSyncOrThrow(String connectionId, ServerPlugin serverPlugin) {
    var pluginKey = serverPlugin.getKey();
    LOG.info("[SYNC] Downloading plugin '{}'", serverPlugin.getFilename());
    var cancelMonitor = new SonarLintCancelMonitor();
    sonarQubeClientManager.withActiveClient(connectionId,
      api -> api.plugins().getPlugin(pluginKey,
        binary -> storageService.connection(connectionId).plugins().store(serverPlugin, binary),
        cancelMonitor));
  }

  public ArtifactOrigin sourceFor(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    var isSonarQubeCloud = connection != null && connection.getKind() == ConnectionKind.SONARCLOUD;
    return isSonarQubeCloud ? ArtifactOrigin.SONARQUBE_CLOUD : ArtifactOrigin.SONARQUBE_SERVER;
  }

  String deduplicationKeyFor(String connectionId, ServerPlugin plugin) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    var serverUrl = connection != null ? connection.getUrl() : connectionId;
    return serverUrl + "/api/plugins/download?plugin=" + plugin.getKey() + "#" + plugin.getHash();
  }
}
