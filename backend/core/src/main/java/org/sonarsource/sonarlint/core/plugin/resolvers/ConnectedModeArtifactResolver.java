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

import java.nio.file.Files;
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
import org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
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

public class ConnectedModeArtifactResolver implements ArtifactResolver, CompanionPluginResolver {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String LEGACY_TYPESCRIPT_PLUGIN_KEY = "typescript";
  private static final String PLUGIN_FETCH_ERROR = "Could not fetch server plugin list for connection '{}'";

  public static final Version CUSTOM_SECRETS_MIN_SQ_VERSION = Version.create("10.4");
  public static final Version ENTERPRISE_IAC_MIN_SQ_VERSION = Version.create("2025.1");
  public static final Version ENTERPRISE_GO_MIN_SQ_VERSION = Version.create("2025.2");

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
  private final LanguageSupportRepository languageSupportRepository;
  /** Keyed by "{connectionId}:{pluginKey}" to dedup per-connection downloads. */
  private final Set<String> inProgressDownloadKeys = ConcurrentHashMap.newKeySet();

  public ConnectedModeArtifactResolver(StorageService storageService,
    ConnectionConfigurationRepository connectionConfigurationRepository,
    SonarQubeClientManager sonarQubeClientManager,
    ServerPluginsCache serverPluginsCache,
    ApplicationEventPublisher eventPublisher,
    ExecutorService downloadExecutor,
    Set<String> skipSyncPluginKeys,
    LanguageSupportRepository languageSupportRepository) {
    this.storageService = storageService;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.serverPluginsCache = serverPluginsCache;
    this.eventPublisher = eventPublisher;
    this.downloadExecutor = downloadExecutor;
    this.skipSyncPluginKeys = skipSyncPluginKeys;
    this.languageSupportRepository = languageSupportRepository;
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
    var pluginKey = language.getPluginKey();
    try {
      return serverPluginsCache.getPlugins(connectionId)
        .flatMap(plugins -> plugins.stream().filter(p -> p.getKey().equals(pluginKey)).findFirst())
        .map(serverPlugin -> resolveFromStorageOrSchedule(connectionId, serverPlugin, language))
        .or(() -> resolveFromStorageByKey(connectionId, pluginKey));
    } catch (ServerRequestException e) {
      LOG.debug(PLUGIN_FETCH_ERROR, connectionId);
      return resolveFromStorageByKey(connectionId, pluginKey);
    }
  }

  @Override
  public Map<String, PluginStatus> resolveCompanionPlugins(@Nullable String connectionId) {
    if (connectionId == null) {
      return Map.of();
    }
    var result = new ConcurrentHashMap<String, PluginStatus>();
    // Include companion plugins already in storage
    storageService.connection(connectionId).plugins().getStoredPluginPathsByKey().entrySet().stream()
      .filter(e -> isCompanionPlugin(e.getKey()))
      .filter(e -> Files.exists(e.getValue()))
      .forEach(e -> {
        var r = toResolvedArtifact(e.getValue(), connectionId);
        result.put(e.getKey(), PluginStatus.forCompanion(e.getKey(), r.state(), r.source(), r.path()));
      });
    // Schedule downloads for server companions not yet in storage
    fetchServerPluginsSafely(connectionId).ifPresent(plugins ->
      plugins.stream()
        .filter(p -> isCompanionPlugin(p.getKey()) && !result.containsKey(p.getKey()))
        .forEach(p -> processCompanionPlugin(connectionId, p, result))
    );
    return result;
  }

  private void processCompanionPlugin(String connectionId, ServerPlugin plugin, ConcurrentHashMap<String, PluginStatus> result) {
    if (LEGACY_TYPESCRIPT_PLUGIN_KEY.equals(plugin.getKey())
        && !languageSupportRepository.isEnabledInConnectedMode(SonarLanguage.TS)) {
      LOG.debug("[SYNC] Code analyzer '{}' is disabled in SonarLint (language not enabled). Skip downloading it.", plugin.getKey());
      return;
    }
    if (!plugin.isSonarLintSupported() && !languageSupportRepository.isForceSynchronized(plugin.getKey())) {
      LOG.debug("[SYNC] Code analyzer '{}' does not support SonarLint. Skip downloading it.", plugin.getKey());
      return;
    }
    scheduleCompanionDownload(connectionId, plugin);
    result.put(plugin.getKey(), PluginStatus.forCompanion(plugin.getKey(), ArtifactState.DOWNLOADING, null, null));
  }

  private void scheduleCompanionDownload(String connectionId, ServerPlugin plugin) {
    var progressKey = connectionId + ":" + plugin.getKey();
    if (inProgressDownloadKeys.add(progressKey)) {
      downloadExecutor.submit(() -> {
        try {
          var state = downloadPluginSync(connectionId, plugin);
          var storedPath = state == ArtifactState.SYNCED
            ? storageService.connection(connectionId).plugins().getStoredPluginPathsByKey().get(plugin.getKey())
            : null;
          var source = isSonarCloud(connectionId) ? ArtifactSource.SONARQUBE_CLOUD : ArtifactSource.SONARQUBE_SERVER;
          eventPublisher.publishEvent(new PluginStatusUpdateEvent(connectionId,
            List.of(PluginStatus.forCompanion(plugin.getKey(), state, source, storedPath))));
        } finally {
          inProgressDownloadKeys.remove(progressKey);
        }
      });
    }
  }

  public static boolean isCompanionPlugin(String pluginKey) {
    return !SonarLanguage.ALL_PLUGIN_KEYS.contains(pluginKey);
  }

  private ResolvedArtifact resolveFromStorageOrSchedule(String connectionId, ServerPlugin serverPlugin, SonarLanguage language) {
    var fromStorage = resolveFromStorage(connectionId, serverPlugin);
    if (fromStorage.isPresent()) {
      LOG.debug("[SYNC] Code analyzer '{}' is up-to-date. Skip downloading it.", serverPlugin.getKey());
      return fromStorage.get();
    }
    return scheduleDownload(connectionId, serverPlugin, language);
  }

  private Optional<ResolvedArtifact> resolveFromStorage(String connectionId, ServerPlugin serverPlugin) {
    var stored = storageService.connection(connectionId).plugins().getStoredPluginsByKey().get(serverPlugin.getKey());
    if (stored == null || !Files.exists(stored.getJarPath()) || !stored.hasSameHash(serverPlugin)) {
      return Optional.empty();
    }
    return Optional.of(toResolvedArtifact(stored.getJarPath(), connectionId));
  }

  private ResolvedArtifact scheduleDownload(String connectionId, ServerPlugin serverPlugin, SonarLanguage language) {
    var progressKey = connectionId + ":" + serverPlugin.getKey();
    if (inProgressDownloadKeys.add(progressKey)) {
      downloadExecutor.submit(() -> asyncDownload(connectionId, serverPlugin, language, progressKey));
    }
    return new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null);
  }

  private Optional<ResolvedArtifact> resolveFromStorageByKey(String connectionId, String pluginKey) {
    var stored = storageService.connection(connectionId).plugins().getStoredPluginsByKey().get(pluginKey);
    if (stored == null || !Files.exists(stored.getJarPath())) {
      return Optional.empty();
    }
    return Optional.of(toResolvedArtifact(stored.getJarPath(), connectionId));
  }

  private Optional<List<ServerPlugin>> fetchServerPluginsSafely(String connectionId) {
    try {
      return serverPluginsCache.getPlugins(connectionId);
    } catch (ServerRequestException e) {
      LOG.debug(PLUGIN_FETCH_ERROR, connectionId);
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
      eventPublisher.publishEvent(new PluginStatusUpdateEvent(connectionId,
        List.of(PluginStatus.forLanguage(language, ArtifactState.SYNCED, source, version, null, storedPath))));
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
    eventPublisher.publishEvent(new PluginStatusUpdateEvent(connectionId,
      List.of(PluginStatus.forLanguage(language, ArtifactState.FAILED, null, null, null, null))));
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
