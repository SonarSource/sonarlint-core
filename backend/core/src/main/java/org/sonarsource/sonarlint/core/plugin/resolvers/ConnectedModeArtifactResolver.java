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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.PluginStatusChangedEvent;
import org.sonarsource.sonarlint.core.plugin.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.PluginJarUtils;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.plugin.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.ServerPluginsCache;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerRequestException;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

public class ConnectedModeArtifactResolver implements ArtifactResolver {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final Version CUSTOM_SECRETS_MIN_SQ_VERSION = Version.create("10.4");
  public static final Version ENTERPRISE_IAC_MIN_SQ_VERSION = Version.create("2025.1");
  public static final Version ENTERPRISE_GO_MIN_SQ_VERSION = Version.create("2025.2");

  public static final String CSHARP_ENTERPRISE_PLUGIN_KEY = "csharpenterprise";
  public static final String CSHARP_OSS_PLUGIN_KEY = "csharp";
  public static final String VBNET_ENTERPRISE_PLUGIN_KEY = "vbnetenterprise";
  public static final String VBNET_OSS_PLUGIN_KEY = "vbnet";

  /** Languages where a sufficiently new server version overrides the embedded plugin. */
  private static final Map<SonarLanguage, Version> FORCE_OVERRIDES_SINCE_VERSION = Map.of(
    SonarLanguage.SECRETS, CUSTOM_SECRETS_MIN_SQ_VERSION,
    SonarLanguage.AZURERESOURCEMANAGER, ENTERPRISE_IAC_MIN_SQ_VERSION,
    SonarLanguage.GO, ENTERPRISE_GO_MIN_SQ_VERSION);

  private final StorageService storageService;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final ServerPluginsCache serverPluginsCache;
  private final ApplicationEventPublisher eventPublisher;
  private final ExecutorService downloadExecutor;
  private final Set<String> skipSyncPluginKeys;
  /** Keyed by "{connectionId}:{pluginKey}" to dedup per-connection downloads. */
  private final Set<String> inProgressDownloadKeys = ConcurrentHashMap.newKeySet();

  public ConnectedModeArtifactResolver(StorageService storageService,
    ConnectionConfigurationRepository connectionConfigurationRepository,
    SonarQubeClientManager sonarQubeClientManager,
    ServerPluginsCache serverPluginsCache,
    ApplicationEventPublisher eventPublisher,
    ExecutorService downloadExecutor,
    Set<String> skipSyncPluginKeys) {
    this.storageService = storageService;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.serverPluginsCache = serverPluginsCache;
    this.eventPublisher = eventPublisher;
    this.downloadExecutor = downloadExecutor;
    this.skipSyncPluginKeys = skipSyncPluginKeys;
  }

  @Override
  public Optional<ResolvedArtifact> resolve(SonarLanguage language, @Nullable String connectionId) {
    if (connectionId == null) {
      return Optional.empty();
    }
    if (!passesLanguageGate(language, connectionId)) {
      if (skipSyncPluginKeys.contains(language.getPluginKey())) {
        LOG.debug("[SYNC] Code analyzer '{}' is embedded in SonarLint. Skip downloading it.", language.getPluginKey());
      }
      return Optional.empty();
    }
    var fromStorage = resolveFromStorage(connectionId, language.getPluginKey());
    if (fromStorage.isPresent()) {
      LOG.debug("[SYNC] Code analyzer '{}' is up-to-date. Skip downloading it.", language.getPluginKey());
      return fromStorage;
    }
    return findServerPlugin(connectionId, language.getPluginKey())
      .map(serverPlugin -> downloadAndResolve(connectionId, serverPlugin));
  }

  @Override
  public Optional<ResolvedArtifact> resolveAsync(SonarLanguage language, @Nullable String connectionId) {
    if (connectionId == null || !passesLanguageGate(language, connectionId)) {
      return Optional.empty();
    }
    var fromStorage = resolveFromStorage(connectionId, language.getPluginKey());
    if (fromStorage.isPresent()) {
      return fromStorage;
    }
    return findServerPlugin(connectionId, language.getPluginKey())
      .map(serverPlugin -> scheduleDownload(connectionId, serverPlugin, language));
  }

  private Optional<ResolvedArtifact> resolveFromStorage(String connectionId, String pluginKey) {
    return Optional.ofNullable(storageService.connection(connectionId).plugins().getStoredPluginPathsByKey().get(pluginKey))
      .map(stored -> toResolvedArtifact(stored, connectionId));
  }

  private ResolvedArtifact downloadAndResolve(String connectionId, ServerPlugin serverPlugin) {
    var state = downloadPluginSync(connectionId, serverPlugin);
    if (state == ArtifactState.SYNCED) {
      var path = storageService.connection(connectionId).plugins().getStoredPluginPathsByKey().get(serverPlugin.getKey());
      return toResolvedArtifact(path, connectionId);
    }
    return new ResolvedArtifact(ArtifactState.FAILED, null, null, null);
  }

  private ResolvedArtifact scheduleDownload(String connectionId, ServerPlugin serverPlugin, SonarLanguage language) {
    var progressKey = connectionId + ":" + serverPlugin.getKey();
    if (inProgressDownloadKeys.add(progressKey)) {
      downloadExecutor.submit(() -> asyncDownload(connectionId, serverPlugin, language, progressKey));
    }
    return new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null);
  }

  private Optional<ServerPlugin> findServerPlugin(String connectionId, String pluginKey) {
    try {
      return serverPluginsCache.getPlugins(connectionId)
        .flatMap(plugins -> plugins.stream().filter(p -> p.getKey().equals(pluginKey)).findFirst());
    } catch (ServerRequestException e) {
      LOG.debug("Could not fetch server plugin list for connection '{}', skipping server-side lookup for '{}'", connectionId, pluginKey);
      return Optional.empty();
    }
  }

  private boolean passesLanguageGate(SonarLanguage language, String connectionId) {
    if (FORCE_OVERRIDES_SINCE_VERSION.containsKey(language)) {
      var minVersion = FORCE_OVERRIDES_SINCE_VERSION.get(language);
      return isSonarCloudOrVersionAtLeast(minVersion, connectionId);
    }
    return !skipSyncPluginKeys.contains(language.getPluginKey());
  }

  private void asyncDownload(String connectionId, ServerPlugin serverPlugin, SonarLanguage language, String progressKey) {
    try {
      downloadPluginAndFireEvent(connectionId, serverPlugin, language);
    } catch (Exception e) {
      LOG.error("Failed to download plugin '{}' for connection '{}'", serverPlugin.getKey(), connectionId, e);
      fireFailedEvent(connectionId, language);
    } finally {
      inProgressDownloadKeys.remove(progressKey);
    }
  }

  private void downloadPluginAndFireEvent(String connectionId, ServerPlugin serverPlugin, SonarLanguage language) {
    var state = downloadPluginSync(connectionId, serverPlugin);
    if (state == ArtifactState.SYNCED) {
      var pluginKey = serverPlugin.getKey();
      var storedPath = storageService.connection(connectionId).plugins().getStoredPluginPathsByKey().get(pluginKey);
      var source = isSonarCloud(connectionId) ? ArtifactSource.SONARQUBE_CLOUD : ArtifactSource.SONARQUBE_SERVER;
      var version = storedPath != null ? PluginJarUtils.readVersion(storedPath) : null;
      eventPublisher.publishEvent(new PluginStatusChangedEvent(connectionId,
        List.of(new PluginStatus(language, ArtifactState.SYNCED, source, version, null, storedPath))));
    } else {
      fireFailedEvent(connectionId, language);
    }
  }

  /**
   * Downloads the given server plugin and stores it. Returns {@link ArtifactState#SYNCED} on
   * success or {@link ArtifactState#FAILED} on error. Does not fire any events.
   */
  public ArtifactState downloadPluginSync(String connectionId, ServerPlugin serverPlugin) {
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

  private void fireFailedEvent(String connectionId, SonarLanguage language) {
    eventPublisher.publishEvent(new PluginStatusChangedEvent(connectionId,
      List.of(new PluginStatus(language, ArtifactState.FAILED, null, null, null, null))));
  }

  private ResolvedArtifact toResolvedArtifact(Path pluginPath, String connectionId) {
    var source = isSonarCloud(connectionId) ? ArtifactSource.SONARQUBE_CLOUD : ArtifactSource.SONARQUBE_SERVER;
    return new ResolvedArtifact(ArtifactState.SYNCED, pluginPath, source, PluginJarUtils.readVersion(pluginPath));
  }

  private boolean isSonarCloudOrVersionAtLeast(Version minVersion, String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    if (connection == null) {
      return false;
    }
    return connection.getKind() == ConnectionKind.SONARCLOUD || storageService.connection(connectionId).serverInfo().read()
      .map(serverInfo -> serverInfo.version().compareToIgnoreQualifier(minVersion) >= 0)
      .orElse(false);
  }

  private boolean isSonarCloud(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    return connection != null && connection.getKind() == ConnectionKind.SONARCLOUD;
  }
}
