/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;

public class PluginsSynchronizer {
  public static final Version CUSTOM_SECRETS_MIN_SQ_VERSION = Version.create("10.4");
  public static final Version ENTERPRISE_IAC_MIN_SQ_VERSION = Version.create("2025.1");
  public static final Version ENTERPRISE_GO_MIN_SQ_VERSION = Version.create("2025.2");
  public static final String CSHARP_ENTERPRISE_PLUGIN_ID = "csharpenterprise";
  public static final String GO_ENTERPRISE_PLUGIN_ID = "goenterprise";
  private static final Set<String> FORCE_SYNCHRONIZED_ANALYZERS = Set.of(
    // SLCORE-1179 Force synchronize "C# Enterprise" after repackaging (SQS 10.8+)
    CSHARP_ENTERPRISE_PLUGIN_ID,
    // SLCORE-1337 Force synchronize "Go Enterprise" before proper repackaging (SQS 2025.2)
    GO_ENTERPRISE_PLUGIN_ID
  );
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Set<String> sonarSourceDisabledPluginKeys;
  private final ConnectionStorage storage;
  private Set<String> embeddedPluginKeys;

  public PluginsSynchronizer(Set<SonarLanguage> enabledLanguages, ConnectionStorage storage, Set<String> embeddedPluginKeys) {
    this.sonarSourceDisabledPluginKeys = getSonarSourceDisabledPluginKeys(enabledLanguages);
    this.storage = storage;
    this.embeddedPluginKeys = embeddedPluginKeys;
  }

  public PluginSynchronizationSummary synchronize(ServerApi serverApi, Version serverVersion, SonarLintCancelMonitor cancelMonitor) {
    var qwirks = VersionSynchronizationQwirks.forServerAndVersion(serverApi, serverVersion);
    var embeddedPluginKeysCopy = new HashSet<>(embeddedPluginKeys);
    if (qwirks.usesIaCEnterprise) {
      embeddedPluginKeysCopy.remove(SonarLanguage.TERRAFORM.getPluginKey());
      embeddedPluginKeys = embeddedPluginKeysCopy;
    }
    if (qwirks.useSecretsFromServer) {
      embeddedPluginKeysCopy.remove(SonarLanguage.SECRETS.getPluginKey());
      embeddedPluginKeys = embeddedPluginKeysCopy;
    }
    if (qwirks.forceSyncGoEnterprise) {
      embeddedPluginKeysCopy.remove(SonarLanguage.GO.getPluginKey());
      embeddedPluginKeys = embeddedPluginKeysCopy;
    }

    var storedPluginsByKey = storage.plugins().getStoredPluginsByKey();
    var serverPlugins = serverApi.plugins().getInstalled(cancelMonitor);
    var downloadSkipReasonByServerPlugin = serverPlugins.stream()
      .collect(Collectors.toMap(Function.identity(), plugin -> determineIfShouldSkipDownload(plugin, storedPluginsByKey)));

    var pluginsToDownload = downloadSkipReasonByServerPlugin.entrySet().stream()
      .filter(entry -> entry.getValue().isEmpty())
      .map(Map.Entry::getKey)
      .toList();
    var serverPluginsExpectedInStorage = downloadSkipReasonByServerPlugin.entrySet().stream()
      .filter(entry -> entry.getValue().isEmpty() || entry.getValue().get().equals(DownloadSkipReason.UP_TO_DATE))
      .map(Map.Entry::getKey)
      .toList();

    if (pluginsToDownload.isEmpty()) {
      storage.plugins().storeNoPlugins();
      storage.plugins().cleanUpUnknownPlugins(serverPluginsExpectedInStorage);
      return new PluginSynchronizationSummary(false);
    }
    downloadAll(serverApi, pluginsToDownload, cancelMonitor);
    storage.plugins().cleanUpUnknownPlugins(serverPluginsExpectedInStorage);
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

  private Optional<DownloadSkipReason> determineIfShouldSkipDownload(ServerPlugin serverPlugin, Map<String, StoredPlugin> storedPluginsByKey) {
    if (embeddedPluginKeys.contains(serverPlugin.getKey())) {
      LOG.debug("[SYNC] Code analyzer '{}' is embedded in SonarLint. Skip downloading it.", serverPlugin.getKey());
      return Optional.of(DownloadSkipReason.EMBEDDED);
    }
    if (upToDate(serverPlugin, storedPluginsByKey)) {
      LOG.debug("[SYNC] Code analyzer '{}' is up-to-date. Skip downloading it.", serverPlugin.getKey());
      return Optional.of(DownloadSkipReason.UP_TO_DATE);
    }
    if (!serverPlugin.isSonarLintSupported() &&
      !isForceSynchronized(serverPlugin.getKey())) {
      LOG.debug("[SYNC] Code analyzer '{}' does not support SonarLint. Skip downloading it.", serverPlugin.getKey());
      return Optional.of(DownloadSkipReason.NOT_SONARLINT_SUPPORTED);
    }
    if (sonarSourceDisabledPluginKeys.contains(serverPlugin.getKey())) {
      LOG.debug("[SYNC] Code analyzer '{}' is disabled in SonarLint (language not enabled). Skip downloading it.", serverPlugin.getKey());
      return Optional.of(DownloadSkipReason.LANGUAGE_NOT_ENABLED);
    }
    return Optional.empty();
  }

  private static boolean isForceSynchronized(String pluginKey) {
    return FORCE_SYNCHRONIZED_ANALYZERS.contains(pluginKey);
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

  private enum DownloadSkipReason {
    EMBEDDED, UP_TO_DATE, NOT_SONARLINT_SUPPORTED, LANGUAGE_NOT_ENABLED
  }

  private record VersionSynchronizationQwirks(boolean useSecretsFromServer, boolean usesIaCEnterprise, boolean  forceSyncGoEnterprise) {
    private static VersionSynchronizationQwirks forServerAndVersion(ServerApi serverApi, Version version) {
      return new VersionSynchronizationQwirks(
        // On SonarQube server 10.4+ and SonarQube Cloud, we need to use the server's text analyzer
        // to support commercial rules (SQC and SQS 10.8+ DE+) and custom secrets (SQS 10.4+ EE+)
        serverApi.isSonarCloud() || version.satisfiesMinRequirement(CUSTOM_SECRETS_MIN_SQ_VERSION),
        serverApi.isSonarCloud() || version.satisfiesMinRequirement(ENTERPRISE_IAC_MIN_SQ_VERSION),
        // On SonarQube server 2025.2+ and SonarQube Cloud, we need to use the server's Go analyzer
        // to support Enterprise rules
        serverApi.isSonarCloud() || version.satisfiesMinRequirement(ENTERPRISE_GO_MIN_SQ_VERSION)
      );
    }
  }
}
