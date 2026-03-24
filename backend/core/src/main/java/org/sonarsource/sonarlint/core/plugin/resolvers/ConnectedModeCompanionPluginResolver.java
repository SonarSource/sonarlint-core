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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.plugin.ServerPluginsCache;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerRequestException;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;

/**
 * Resolves companion plugins (plugins that are not strictly language analyzers but
 * provide additional features required by other analyzers, like sonarlint-omnisharp).
 * It delegates actual plugin downloading to the {@link ServerPluginDownloader} but
 * orchestrates the specific logic required to ensure companion plugins are available
 * and up-to-date in Connected Mode.
 * <p>
 * Also includes companions that are present in local storage but absent from the server list,
 * so that already-downloaded plugins remain usable when the server is unreachable.
 * </p>
 */
public class ConnectedModeCompanionPluginResolver implements CompanionPluginResolver {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String LEGACY_TYPESCRIPT_PLUGIN_KEY = "typescript";
  private static final String PLUGIN_FETCH_ERROR = "Could not fetch server plugin list for connection '{}'";

  private final StorageService storageService;
  private final ServerPluginsCache serverPluginsCache;
  private final ServerPluginDownloader downloader;
  private final LanguageSupportRepository languageSupportRepository;

  public ConnectedModeCompanionPluginResolver(StorageService storageService,
      ServerPluginsCache serverPluginsCache,
      ServerPluginDownloader downloader,
      LanguageSupportRepository languageSupportRepository) {
    this.storageService = storageService;
    this.serverPluginsCache = serverPluginsCache;
    this.downloader = downloader;
    this.languageSupportRepository = languageSupportRepository;
  }

  @Override
  public Map<String, PluginStatus> resolveCompanionPlugins(@Nullable String connectionId) {
    if (connectionId == null) {
      return Map.of();
    }
    var result = new ConcurrentHashMap<String, PluginStatus>();
    var storedPluginsByKey = storageService.connection(connectionId).plugins().getStoredPluginsByKey();
    fetchServerPluginsSafely(connectionId).ifPresent(plugins ->
      plugins.stream()
        .filter(p -> isCompanionPlugin(p.getKey()))
        .forEach(p -> resolveOrScheduleCompanion(connectionId, p, storedPluginsByKey, result))
    );
    // Include stored companions not already resolved: covers server-unreachable and companions removed from server
    storedPluginsByKey.entrySet().stream()
      .filter(e -> isCompanionPlugin(e.getKey()))
      .filter(e -> !result.containsKey(e.getKey()))
      .filter(e -> Files.exists(e.getValue().getJarPath()))
      .forEach(e -> addStoredCompanion(connectionId, e.getKey(), e.getValue(), result));
    return result;
  }

  private void resolveOrScheduleCompanion(String connectionId, ServerPlugin plugin,
      Map<String, StoredPlugin> storedPluginsByKey, ConcurrentHashMap<String, PluginStatus> result) {
    var stored = storedPluginsByKey.get(plugin.getKey());
    if (stored != null && Files.exists(stored.getJarPath()) && stored.hasSameHash(plugin)) {
      addStoredCompanion(connectionId, plugin.getKey(), stored, result);
    } else {
      processCompanionPlugin(connectionId, plugin, result);
    }
  }

  private void addStoredCompanion(String connectionId, String key, StoredPlugin stored, ConcurrentHashMap<String, PluginStatus> result) {
    var source = downloader.sourceFor(connectionId);
    result.put(key, PluginStatus.forCompanion(key, ArtifactState.SYNCED, source, stored.getJarPath()));
  }

  private void processCompanionPlugin(String connectionId, ServerPlugin plugin, ConcurrentHashMap<String, PluginStatus> result) {
    if (LEGACY_TYPESCRIPT_PLUGIN_KEY.equals(plugin.getKey())
        && !languageSupportRepository.getEnabledLanguagesInConnectedMode().contains(SonarLanguage.TS)) {
      LOG.debug("[SYNC] Code analyzer '{}' is disabled in SonarLint (language not enabled). Skip downloading it.", plugin.getKey());
      return;
    }
    if (!plugin.isSonarLintSupported() && !isForceSynchronized(plugin.getKey())) {
      LOG.debug("[SYNC] Code analyzer '{}' does not support SonarLint. Skip downloading it.", plugin.getKey());
      return;
    }
    downloader.scheduleCompanionPluginDownload(connectionId, plugin);
    result.put(plugin.getKey(), PluginStatus.forCompanion(plugin.getKey(), ArtifactState.DOWNLOADING, null, null));
  }

  private boolean isForceSynchronized(String pluginKey) {
    if ("csharpenterprise".equals(pluginKey)) {
      return languageSupportRepository.getEnabledLanguagesInConnectedMode().contains(SonarLanguage.CS);
    }
    if ("goenterprise".equals(pluginKey)) {
      return languageSupportRepository.getEnabledLanguagesInConnectedMode().contains(SonarLanguage.GO);
    }
    return false;
  }

  private static boolean isCompanionPlugin(String pluginKey) {
    return !SonarLanguage.containsPlugin(pluginKey);
  }

  private Optional<java.util.List<ServerPlugin>> fetchServerPluginsSafely(String connectionId) {
    try {
      return serverPluginsCache.getPlugins(connectionId);
    } catch (ServerRequestException e) {
      LOG.debug(PLUGIN_FETCH_ERROR, connectionId);
      return Optional.empty();
    }
  }
}
