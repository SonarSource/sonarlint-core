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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.plugins.EnterpriseReplacement;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.plugin.PluginJarUtils;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownload;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;

/**
 * Artifact source backed by a specific SonarQube Server or SonarQube Cloud connection.
 *
 * <p>One instance is created per connection. The connection identifier is fixed at construction
 * time; no connection parameter is passed at call time.</p>
 *
 * <p>{@link #listAvailableArtifacts(Set)} queries the server for available plugins and filters
 * them by enabled languages and SonarLint compatibility. Language plugins are included if any of
 * their languages is enabled. Companion/unknown plugins are included if they are marked
 * {@code sonarLintSupported} by the server.</p>
 *
 * <p>The {@link AvailableArtifact#isEnterprise()} flag is set to {@code true} when the server
 * is serving the enterprise edition of a plugin on this connection:
 * <ul>
 *   <li>Different-key enterprise variants ({@code csharpenterprise}, {@code vbnetenterprise}):
 *       always enterprise.</li>
 *   <li>Same-key enterprise plugins (GO, IAC, TEXT): enterprise when the connection qualifies
 *       (SonarQube Cloud, or SonarQube Server &ge; the minimum version from
 *       {@link EnterpriseReplacement}).</li>
 * </ul>
 *
 * <p>Each returned artifact is local when a stored JAR with the expected hash exists, or remote
 * with a blocking download operation otherwise. The source does <em>not</em> apply the skip-list
 * check — that is the responsibility of the loading strategy that owns this source.</p>
 */
public class ServerPluginSource implements ArtifactSource {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String PLUGIN_FETCH_ERROR = "Could not fetch server plugin list for connection '{}'";

  private final String connectionId;
  private final StorageService storageService;
  private final ServerPluginsCache serverPluginsCache;
  private final ServerPluginDownloader downloader;

  public ServerPluginSource(String connectionId, StorageService storageService,
    ServerPluginsCache serverPluginsCache, ServerPluginDownloader downloader) {
    this.connectionId = connectionId;
    this.storageService = storageService;
    this.serverPluginsCache = serverPluginsCache;
    this.downloader = downloader;
  }

  /**
   * Returns all server plugins that are eligible:
   * <ul>
   *   <li>if a plugin is known (see {@link SonarPlugin}), at least one of its languages should be currently enabled
   *   <li>if it is unknown, it needs to be SonarLint-Supported
   * </ul>
   */
  @Override
  public List<AvailableArtifact> listAvailableArtifacts(Set<SonarLanguage> enabledLanguages) {
    var storedPlugins = loadStoredPlugins();
    return fetchServerPluginsSafely().stream()
      .filter(plugin -> isEligible(plugin, enabledLanguages))
      .map(plugin -> toAvailableArtifact(plugin, storedPlugins))
      .toList();
  }

  private AvailableArtifact toAvailableArtifact(ServerPlugin plugin, Map<String, StoredPlugin> storedPlugins) {
    var stored = findStoredPlugin(plugin.getKey(), storedPlugins).filter(candidate -> candidate.hasSameHash(plugin));
    stored.ifPresent(ignored -> LOG.debug("[SYNC] Code analyzer '{}' is up-to-date. Skip downloading it.", plugin.getKey()));
    ArtifactLocation location = stored
      .<ArtifactLocation>map(candidate -> toLocalLocation(candidate.getJarPath()))
      .orElseGet(() -> new ArtifactLocation.Remote(new ServerDownload(plugin)));
    return new AvailableArtifact(plugin.getKey(), null, isEnterprisePlugin(plugin.getKey()), SonarPlugin.findByKey(plugin.getKey()), location);
  }

  private class ServerDownload implements ArtifactDownload {
    private final ServerPlugin plugin;

    private ServerDownload(ServerPlugin plugin) {
      this.plugin = plugin;
    }

    @Override
    public String deduplicationKey() {
      return downloader.deduplicationKeyFor(connectionId, plugin);
    }

    @Override
    public ArtifactLocation.Local download() {
      downloader.downloadPluginSyncOrThrow(connectionId, plugin);
      var path = storageService.connection(connectionId).plugins().getStoredPluginPathsByKey().get(plugin.getKey());
      if (path == null) {
        throw new IllegalStateException("Downloaded plugin was not found in storage: " + plugin.getKey());
      }
      return toLocalLocation(path);
    }
  }

  private ArtifactLocation.Local toLocalLocation(Path pluginPath) {
    return new ArtifactLocation.Local(pluginPath, downloader.sourceFor(connectionId), PluginJarUtils.readVersion(pluginPath));
  }

  private static boolean isEligible(ServerPlugin plugin, Set<SonarLanguage> enabledLanguages) {
    return SonarPlugin.findByKey(plugin.getKey())
      .map(sonarPlugin -> {
        var languages = sonarPlugin.getLanguages();
        return !languages.isEmpty() && languages.stream().anyMatch(lang -> lang.shouldSyncInConnectedMode() && enabledLanguages.contains(lang));
      })
      .orElseGet(plugin::isSonarLintSupported);
  }

  /**
   * Returns {@code true} if the given plugin key is served as its enterprise edition on this
   * connection.
   *
   * <p>Different-key enterprise variants ({@code csharpenterprise}, {@code vbnetenterprise}) are
   * always enterprise. Same-key enterprise plugins (GO, IAC, TEXT) are enterprise when the
   * connection is SonarQube Cloud, or when the stored server version meets the minimum.</p>
   */
  private boolean isEnterprisePlugin(String key) {
    if (SonarPlugin.isEnterpriseVariant(key)) {
      return true;
    }
    return SonarPlugin.findByKey(key)
      .flatMap(SonarPlugin::getEnterpriseReplacement)
      .map(this::hasEnterpriseReplacement)
      .orElse(false);
  }

  private boolean hasEnterpriseReplacement(EnterpriseReplacement replacement) {
    var source = downloader.sourceFor(connectionId);
    if (source == ArtifactOrigin.SONARQUBE_CLOUD) {
      return replacement.onSonarQubeCloud();
    }
    var replacementStartingInSonarQubeServerVersion = replacement.startingSonarQubeServerVersion();
    return replacementStartingInSonarQubeServerVersion != null && storageService.connection(connectionId).serverInfo().read()
      .map(info -> info.version().compareTo(replacementStartingInSonarQubeServerVersion) >= 0)
      .orElse(false);
  }

  private static Optional<StoredPlugin> findStoredPlugin(String pluginKey, Map<String, StoredPlugin> storedPlugins) {
    return Optional.ofNullable(storedPlugins.get(pluginKey))
      .filter(plugin -> Files.exists(plugin.getJarPath()));
  }

  private Map<String, StoredPlugin> loadStoredPlugins() {
    try {
      return storageService.connection(connectionId).plugins().getStoredPluginsByKey();
    } catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  private List<ServerPlugin> fetchServerPluginsSafely() {
    try {
      return serverPluginsCache.getPlugins(connectionId).orElse(List.of());
    } catch (Exception e) {
      LOG.debug(PLUGIN_FETCH_ERROR, connectionId);
      return storedPluginsAsServerPlugins();
    }
  }

  private List<ServerPlugin> storedPluginsAsServerPlugins() {
    return loadStoredPlugins().values().stream()
      .filter(s -> Files.exists(s.getJarPath()))
      .map(s -> new ServerPlugin(s.getKey(), s.getHash(), s.getJarPath().getFileName().toString(), true))
      .toList();
  }
}
