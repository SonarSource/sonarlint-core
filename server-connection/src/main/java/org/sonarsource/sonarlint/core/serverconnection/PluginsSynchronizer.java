/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.PluginsMinVersions;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;

public class PluginsSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Set<String> sonarSourceDisabledPluginKeys;
  private final PluginsStorage pluginsStorage;
  private final Set<String> embeddedPluginKeys;
  private final PluginsMinVersions pluginsMinVersions = new PluginsMinVersions();

  public PluginsSynchronizer(Set<Language> enabledLanguages, PluginsStorage pluginsStorage, Set<String> embeddedPluginKeys) {
    this.sonarSourceDisabledPluginKeys = getSonarSourceDisabledPluginKeys(enabledLanguages);
    this.pluginsStorage = pluginsStorage;
    this.embeddedPluginKeys = embeddedPluginKeys;
  }

  public boolean synchronize(ServerApi serverApi, ProgressMonitor progressMonitor) {
    var storedPluginsByKey = pluginsStorage.getStoredPluginsByKey();
    var serverPlugins = serverApi.plugins().getInstalled();
    var pluginsToDownload = serverPlugins.stream()
      .filter(p -> shouldDownload(p, storedPluginsByKey))
      .collect(Collectors.toList());
    downloadAll(serverApi, pluginsToDownload, progressMonitor);
    return !pluginsToDownload.isEmpty();
  }

  private void downloadAll(ServerApi serverApi, List<ServerPlugin> pluginsToDownload, ProgressMonitor progressMonitor) {
    var i = 0;
    for (ServerPlugin p : pluginsToDownload) {
      progressMonitor.setProgressAndCheckCancel("Downloading analyzer '" + p.getKey() + "'", i++ / (float) pluginsToDownload.size());
      downloadPlugin(serverApi, p);
    }
  }

  private void downloadPlugin(ServerApi serverApi, ServerPlugin plugin) {
    LOG.info("[SYNC] Downloading plugin '{}'", plugin.getFilename());
    serverApi.plugins().getPlugin(plugin.getKey(), pluginBinary -> pluginsStorage.store(plugin, pluginBinary));
  }

  private boolean shouldDownload(ServerPlugin serverPlugin, Map<String, StoredPlugin> storedPluginsByKey) {
    if (embeddedPluginKeys.contains(serverPlugin.getKey())) {
      LOG.debug("[SYNC] Code analyzer '{}' is embedded in SonarLint. Skip downloading it.", serverPlugin.getKey());
      return false;
    }
    if (upToDate(serverPlugin, storedPluginsByKey)) {
      LOG.debug("[SYNC] Code analyzer '{}' is up-to-date. Skip downloading it.", serverPlugin.getKey());
      return false;
    }
    if (!serverPlugin.isSonarLintSupported()) {
      LOG.debug("[SYNC] Code analyzer '{}' does not support SonarLint. Skip downloading it.", serverPlugin.getKey());
      return false;
    }
    if (sonarSourceDisabledPluginKeys.contains(serverPlugin.getKey())) {
      LOG.debug("[SYNC] Code analyzer '{}' is disabled in SonarLint (language not enabled). Skip downloading it.", serverPlugin.getKey());
      return false;
    }
    var pluginVersion = VersionUtils.getJarVersion(serverPlugin.getFilename());
    if (!pluginsMinVersions.isVersionSupported(serverPlugin.getKey(), pluginVersion)) {
      var minimumVersion = pluginsMinVersions.getMinimumVersion(serverPlugin.getKey());
      LOG.debug("[SYNC] Code analyzer '{}' version '{}' is not supported (minimal version is '{}'). Skip downloading it.",
        serverPlugin.getKey(), pluginVersion, minimumVersion);
      return false;
    }
    return true;
  }

  private static boolean upToDate(ServerPlugin serverPlugin, Map<String, StoredPlugin> storedPluginsByKey) {
    return storedPluginsByKey.containsKey(serverPlugin.getKey())
      && storedPluginsByKey.get(serverPlugin.getKey()).hasSameHash(serverPlugin);
  }

  private static final String OLD_SONARTS_PLUGIN_KEY = "typescript";

  private static Set<String> getSonarSourceDisabledPluginKeys(Set<Language> enabledLanguages) {
    var languagesByPluginKey = Arrays.stream(Language.values()).collect(Collectors.groupingBy(Language::getPluginKey));
    var disabledPluginKeys = languagesByPluginKey.entrySet().stream()
      .filter(e -> Collections.disjoint(enabledLanguages, e.getValue()))
      .map(Map.Entry::getKey)
      .collect(Collectors.toSet());
    if (!enabledLanguages.contains(Language.TS)) {
      // Special case for old TS plugin
      disabledPluginKeys.add(OLD_SONARTS_PLUGIN_KEY);
    }
    return disabledPluginKeys;
  }
}
