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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.PluginJarUtils;
import org.sonarsource.sonarlint.core.plugin.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.ServerPluginsCache;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;

/**
 * Resolves analyzer plugins from the local storage of a SQS or SQC server connection.
 *
 * <p>{@link #resolve} handles language plugins. It returns immediately: if the locally stored copy is up-to-date its path is returned as
 * {@link ArtifactState#SYNCED}; otherwise a background download is scheduled and
 * {@link ArtifactState#DOWNLOADING} is returned. Concurrent downloads for the same
 * connection + plugin key are deduplicated by the underlying downloader.</p>
 *
 * <p><b>Events:</b> when a background download completes, a {@link org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent}
 * is published — {@link ArtifactState#SYNCED} on success, {@link ArtifactState#FAILED} on
 * error.</p>
 */
public class ConnectedModeArtifactResolver implements ArtifactResolver {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String PLUGIN_FETCH_ERROR = "Could not fetch server plugin list for connection '{}'";

  private final StorageService storageService;
  private final ServerPluginsCache serverPluginsCache;
  private final ServerPluginDownloader downloader;
  private final PluginOverrideRegistry overrideRegistry;
  private final Set<String> skipSyncPluginKeys;

  public ConnectedModeArtifactResolver(StorageService storageService, ServerPluginsCache serverPluginsCache,
    ServerPluginDownloader downloader, PluginOverrideRegistry overrideRegistry, InitializeParams initializeParams) {
    this.storageService = storageService;
    this.serverPluginsCache = serverPluginsCache;
    this.downloader = downloader;
    this.overrideRegistry = overrideRegistry;
    this.skipSyncPluginKeys = initializeParams.getConnectedModeEmbeddedPluginPathsByKey().keySet();
  }

  private boolean isOverriddenByServer(SonarLanguage language, String connectionId, Map<String, StoredPlugin> storedPlugins) {
    var pluginKey = language.getPlugin().getKey();
    if (overrideRegistry.getEnterpriseOverrideKey(pluginKey, connectionId).isPresent()) {
      return true;
    }
    return !"iac".equals(pluginKey) && (storedPlugins.containsKey(pluginKey + "enterprise")
      || storedPlugins.containsKey(pluginKey + "developer"));
  }

  private boolean passesLanguageGate(SonarLanguage language, String connectionId, Map<String, StoredPlugin> storedPlugins) {
    if (isOverriddenByServer(language, connectionId, storedPlugins)) {
      return true;
    }
    if (skipSyncPluginKeys.contains(language.getPlugin().getKey())) {
      return false;
    }
    return language.shouldSyncInConnectedMode();
  }

  private Map<String, StoredPlugin> loadStoredPlugins(String connectionId) {
    try {
      return storageService.connection(connectionId).plugins().getStoredPluginsByKey();
    } catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  @Override
  public Optional<ResolvedArtifact> resolve(SonarLanguage language, @Nullable String connectionId) {
    if (connectionId == null) {
      return Optional.empty();
    }
    var storedPlugins = loadStoredPlugins(connectionId);
    var pluginKey = language.getPlugin().getKey();
    if (!passesLanguageGate(language, connectionId, storedPlugins)) {
      if (skipSyncPluginKeys.contains(pluginKey)) {
        LOG.debug("[SYNC] Code analyzer '{}' is embedded in SonarLint. Skip downloading it.", pluginKey);
      }
      return Optional.empty();
    }

    var enterpriseOverrideKey = overrideRegistry.getEnterpriseOverrideKey(pluginKey, connectionId).orElse(null);
    var primaryKey = enterpriseOverrideKey != null ? enterpriseOverrideKey : pluginKey;
    var fallbackKey = deriveFallbackKey(pluginKey, enterpriseOverrideKey);
    try {
      return serverPluginsCache.getPlugins(connectionId)
        .flatMap(plugins -> findServerPlugin(plugins, primaryKey, fallbackKey))
        .map(serverPlugin -> resolveFromStorageOrSchedule(connectionId, serverPlugin, storedPlugins, language))
        .or(() -> resolveFromStorageWithFallback(connectionId, primaryKey, fallbackKey, storedPlugins));
    } catch (Exception e) {
      LOG.debug(PLUGIN_FETCH_ERROR, connectionId);
      return resolveFromStorageWithFallback(connectionId, primaryKey, fallbackKey, storedPlugins);
    }
  }

  @Nullable
  private static String deriveFallbackKey(String basePluginKey, @Nullable String enterpriseOverrideKey) {
    if (enterpriseOverrideKey != null) {
      // When override is active, fall back to the base key (e.g., textenterprise -> text)
      return basePluginKey;
    }
    // For languages whose pluginKey is already an enterprise key (e.g., ANSIBLE with "iacenterprise"),
    // fall back to the base key
    if ("iacenterprise".equals(basePluginKey)) {
      return "iac";
    }
    return null;
  }

  private static Optional<ServerPlugin> findServerPlugin(List<ServerPlugin> plugins, String primaryKey,
    @Nullable String fallbackKey) {
    var match = plugins.stream().filter(p -> p.getKey().equals(primaryKey)).findFirst();
    if (match.isEmpty() && fallbackKey != null) {
      match = plugins.stream().filter(p -> p.getKey().equals(fallbackKey)).findFirst();
    }
    return match;
  }

  private ResolvedArtifact resolveFromStorageOrSchedule(String connectionId, ServerPlugin serverPlugin,
    Map<String, StoredPlugin> storedPlugins, SonarLanguage language) {
    var fromStorage = resolveFromStorage(connectionId, serverPlugin, storedPlugins);
    if (fromStorage.isPresent()) {
      LOG.debug("[SYNC] Code analyzer '{}' is up-to-date. Skip downloading it.", serverPlugin.getKey());
      return fromStorage.get();
    }
    downloader.scheduleLanguagePluginDownload(connectionId, serverPlugin, language);
    return new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null);
  }

  private Optional<ResolvedArtifact> resolveFromStorage(String connectionId, ServerPlugin serverPlugin, Map<String, StoredPlugin> storedPlugins) {
    return findStoredPlugin(serverPlugin.getKey(), storedPlugins)
      .filter(s -> s.hasSameHash(serverPlugin))
      .map(s -> toResolvedArtifact(s.getJarPath(), connectionId));
  }

  private Optional<ResolvedArtifact> resolveFromStorageByKey(String connectionId, String pluginKey, Map<String, StoredPlugin> storedPlugins) {
    return findStoredPlugin(pluginKey, storedPlugins)
      .map(s -> toResolvedArtifact(s.getJarPath(), connectionId));
  }

  private Optional<ResolvedArtifact> resolveFromStorageWithFallback(String connectionId, String pluginKey,
    @Nullable String fallbackPluginKey, Map<String, StoredPlugin> storedPlugins) {
    return resolveFromStorageByKey(connectionId, pluginKey, storedPlugins)
      .or(() -> fallbackPluginKey != null ? resolveFromStorageByKey(connectionId, fallbackPluginKey, storedPlugins) : Optional.empty());
  }

  private static Optional<StoredPlugin> findStoredPlugin(String pluginKey, Map<String, StoredPlugin> storedPlugins) {
    var stored = storedPlugins.get(pluginKey);
    if (stored == null || !Files.exists(stored.getJarPath())) {
      return Optional.empty();
    }
    return Optional.of(stored);
  }

  private ResolvedArtifact toResolvedArtifact(Path pluginPath, String connectionId) {
    var source = downloader.sourceFor(connectionId);
    return new ResolvedArtifact(ArtifactState.SYNCED, pluginPath, source, PluginJarUtils.readVersion(pluginPath));
  }

}
