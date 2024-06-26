/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.PluginsMinVersions;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;

public class PluginsSynchronizer {
  public static final Version CUSTOM_SECRETS_MIN_SQ_VERSION = Version.create("10.4");
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Set<String> sonarSourceDisabledPluginKeys;
  private final ConnectionStorage storage;
  private final PluginsMinVersions pluginsMinVersions = new PluginsMinVersions();
  private Set<String> embeddedPluginKeys;

  public PluginsSynchronizer(Set<SonarLanguage> enabledLanguages, ConnectionStorage storage, Set<String> embeddedPluginKeys) {
    this.sonarSourceDisabledPluginKeys = getSonarSourceDisabledPluginKeys(enabledLanguages);
    this.storage = storage;
    this.embeddedPluginKeys = embeddedPluginKeys;
  }

  public PluginSynchronizationSummary synchronize(ServerApi serverApi, boolean supportsCustomSecrets, SonarLintCancelMonitor cancelMonitor) {
    if (supportsCustomSecrets) {
      var embeddedPluginKeysCopy = new HashSet<>(embeddedPluginKeys);
      embeddedPluginKeysCopy.remove(SonarLanguage.SECRETS.getPluginKey());
      embeddedPluginKeys = embeddedPluginKeysCopy;
    }

    var storedPluginsByKey = storage.plugins().getStoredPluginsByKey();
    var serverPlugins = serverApi.plugins().getInstalled(cancelMonitor);
    var pluginsToDownload = serverPlugins.stream()
      .filter(p -> shouldDownload(p, storedPluginsByKey))
      .collect(Collectors.toList());

    if (pluginsToDownload.isEmpty()) {
      storage.plugins().storeNoPlugins();
      return new PluginSynchronizationSummary(false);
    }
    downloadAll(serverApi, pluginsToDownload, cancelMonitor);
    return new PluginSynchronizationSummary(true);
  }

  private void downloadAll(ServerApi serverApi, List<ServerPlugin> pluginsToDownload, SonarLintCancelMonitor cancelMonitor) {
    for (ServerPlugin p : pluginsToDownload) {
      downloadPlugin(serverApi, p, cancelMonitor);
    }
  }

  private void downloadPlugin(ServerApi serverApi, ServerPlugin plugin, SonarLintCancelMonitor cancelMonitor) {
    LOG.info("[SYNC] Downloading plugin '{}'", plugin.getFilename());
    serverApi.plugins().getPlugin(plugin.getKey(), pluginBinary -> storage.plugins().store(plugin, pluginBinary), cancelMonitor);
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

  private static Set<String> getSonarSourceDisabledPluginKeys(Set<SonarLanguage> enabledLanguages) {
    var languagesByPluginKey = Arrays.stream(SonarLanguage.values()).collect(Collectors.groupingBy(SonarLanguage::getPluginKey));
    var disabledPluginKeys = languagesByPluginKey.entrySet().stream()
      .filter(e -> Collections.disjoint(enabledLanguages, e.getValue()))
      .map(Map.Entry::getKey)
      .collect(Collectors.toSet());
    if (!enabledLanguages.contains(SonarLanguage.TS)) {
      // Special case for old TS plugin
      disabledPluginKeys.add(OLD_SONARTS_PLUGIN_KEY);
    }
    return disabledPluginKeys;
  }
}
