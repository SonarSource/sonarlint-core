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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent;
import org.sonarsource.sonarlint.core.plugin.PluginJarUtils;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.UniqueTaskExecutor;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Handles the background downloading of server plugins (both language and companion plugins).
 * Manages concurrent requests deduplication and publishes plugin status updates upon completion.
 */
public class ServerPluginDownloader {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final StorageService storageService;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final UniqueTaskExecutor uniqueTaskExecutor;

  public ServerPluginDownloader(StorageService storageService, SonarQubeClientManager sonarQubeClientManager,
    ConnectionConfigurationRepository connectionConfigurationRepository, ApplicationEventPublisher eventPublisher,
    ExecutorService downloadExecutor) {
    this.storageService = storageService;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.eventPublisher = eventPublisher;
    this.uniqueTaskExecutor = new UniqueTaskExecutor(downloadExecutor);
  }

  public CompletableFuture<Void> schedulePluginDownload(String connectionId, ServerPlugin serverPlugin) {
    var sonarPlugin = SonarPlugin.findByKey(serverPlugin.getKey());
    return sonarPlugin.isPresent() ? scheduleSonarPluginDownload(connectionId, serverPlugin, sonarPlugin.get())
      : scheduleUnknownPluginDownload(connectionId, serverPlugin);
  }

  private CompletableFuture<Void> scheduleSonarPluginDownload(String connectionId, ServerPlugin serverPlugin, SonarPlugin sonarPlugin) {
    var progressKey = connectionId + ":" + serverPlugin.getKey();
    return uniqueTaskExecutor.scheduleIfAbsent(progressKey, () -> asyncDownload(connectionId, serverPlugin, sonarPlugin));
  }

  private CompletableFuture<Void> scheduleUnknownPluginDownload(String connectionId, ServerPlugin plugin) {
    var progressKey = connectionId + ":" + plugin.getKey();
    return uniqueTaskExecutor.scheduleIfAbsent(progressKey, () -> asyncUnknownPluginDownload(connectionId, plugin));
  }

  private void asyncDownload(String connectionId, ServerPlugin serverPlugin, SonarPlugin sonarPlugin) {
    try {
      downloadPluginAndFireEvent(connectionId, serverPlugin, sonarPlugin);
    } catch (Exception e) {
      LOG.error("Failed to download plugin '{}' for connection '{}'", serverPlugin.getKey(), connectionId, e);
      fireFailedEvent(connectionId, sonarPlugin);
    }
  }

  private void asyncUnknownPluginDownload(String connectionId, ServerPlugin plugin) {
    try {
      downloadUnknownPluginAndFireEvent(connectionId, plugin);
    } catch (Exception e) {
      LOG.error("Failed to download companion plugin '{}' for connection '{}'", plugin.getKey(), connectionId, e);
      eventPublisher.publishEvent(new PluginStatusUpdateEvent(connectionId,
        List.of(PluginStatus.forCompanion(plugin.getKey(), ArtifactState.FAILED, null, null, null))));
    }
  }

  private void downloadPluginAndFireEvent(String connectionId, ServerPlugin serverPlugin, SonarPlugin sonarPlugin) {
    var state = downloadPluginSync(connectionId, serverPlugin);
    if (state == ArtifactState.SYNCED) {
      var pluginKey = serverPlugin.getKey();
      var storedPath = storageService.connection(connectionId).plugins().getStoredPluginPathsByKey().get(pluginKey);
      var source = sourceFor(connectionId);
      var version = storedPath != null ? PluginJarUtils.readVersion(storedPath) : null;
      var statuses = sonarPlugin.getLanguages().stream()
        .map(l -> PluginStatus.forLanguage(l, ArtifactState.SYNCED, source, version, null, storedPath, null))
        .toList();
      eventPublisher.publishEvent(new PluginStatusUpdateEvent(connectionId, statuses));
    } else {
      fireFailedEvent(connectionId, sonarPlugin);
    }
  }

  private void downloadUnknownPluginAndFireEvent(String connectionId, ServerPlugin plugin) {
    var state = downloadPluginSync(connectionId, plugin);
    var storedPath = state == ArtifactState.SYNCED
      ? storageService.connection(connectionId).plugins().getStoredPluginPathsByKey().get(plugin.getKey())
      : null;
    var source = sourceFor(connectionId);
    eventPublisher.publishEvent(new PluginStatusUpdateEvent(connectionId,
      List.of(PluginStatus.forCompanion(plugin.getKey(), state, source, storedPath, null))));
  }

  ArtifactState downloadPluginSync(String connectionId, ServerPlugin serverPlugin) {
    var pluginKey = serverPlugin.getKey();
    LOG.info("[SYNC] Downloading plugin '{}'", serverPlugin.getFilename());
    try {
      var cancelMonitor = new SonarLintCancelMonitor();
      sonarQubeClientManager.withActiveClient(connectionId,
        api -> api.plugins().getPlugin(pluginKey,
          binary -> storageService.connection(connectionId).plugins().store(serverPlugin, binary),
          cancelMonitor));
      return ArtifactState.SYNCED;
    } catch (Exception e) {
      LOG.error("Failed to download plugin '{}' for connection '{}'", pluginKey, connectionId, e);
      return ArtifactState.FAILED;
    }
  }

  private void fireFailedEvent(String connectionId, SonarPlugin sonarPlugin) {
    var statuses = sonarPlugin.getLanguages().stream()
      .map(PluginStatus::failed)
      .toList();
    eventPublisher.publishEvent(new PluginStatusUpdateEvent(connectionId, statuses));
  }

  public ArtifactOrigin sourceFor(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    var isSonarQubeCloud = connection != null && connection.getKind() == ConnectionKind.SONARCLOUD;
    return isSonarQubeCloud ? ArtifactOrigin.SONARQUBE_CLOUD : ArtifactOrigin.SONARQUBE_SERVER;
  }

}
