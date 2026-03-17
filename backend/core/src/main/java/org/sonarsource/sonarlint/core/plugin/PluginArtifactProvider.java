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
package org.sonarsource.sonarlint.core.plugin;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent;
import org.sonarsource.sonarlint.core.plugin.ondemand.DownloadableArtifact;
import org.sonarsource.sonarlint.core.plugin.ondemand.OnDemandArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.ArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.ConnectedModeArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedExtraArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.ExtraArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.PremiumArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.UnsupportedArtifactResolver;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Resolves the analyzer JAR (and any extra dependencies) for each supported language by walking
 * a priority-ordered chain of {@link ArtifactResolver}s.
 *
 * <p>Resolution priority (highest → lowest):
 * <ol>
 *   <li>{@link UnsupportedArtifactResolver} — short-circuits to UNSUPPORTED for languages not enabled
 *       in the applicable mode, or whose plugin key is in the client-provided disable list.</li>
 *   <li>{@link ConnectedModeArtifactResolver} — resolves from server-synced storage. Enterprise
 *       languages (Secrets / IaC / Go) are resolved when the server is new enough; other languages
 *       are resolved unless the IDE provides an embedded override for them.</li>
 *   <li>{@link EmbeddedArtifactResolver} — IDE-bundled plugins for languages the IDE wants to
 *       override in connected mode.</li>
 *   <li>{@link OnDemandArtifactResolver} — on-demand download (e.g. CFamily).</li>
 *   <li>{@link PremiumArtifactResolver} — sentinel for languages that require a connected-mode
 *       server but are not available locally.</li>
 * </ol>
 */
@Component
public class PluginArtifactProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String STANDALONE = "STANDALONE";

  private static final Set<String> CSHARP_OMNISHARP_ARTIFACT_KEYS = Set.of(
    DownloadableArtifact.OMNISHARP_MONO.artifactKey(),
    DownloadableArtifact.OMNISHARP_NET6.artifactKey(),
    DownloadableArtifact.OMNISHARP_WIN.artifactKey());

  private final List<ArtifactResolver> resolvers;
  private final List<ExtraArtifactResolver> extraResolvers;
  private final ConnectedModeArtifactResolver connectedModeArtifactResolver;
  private final EmbeddedArtifactResolver embeddedArtifactResolver;
  private final ServerPluginsCache serverPluginsCache;
  private final StorageService storageService;
  private final ApplicationEventPublisher eventPublisher;
  private final Map<String, Map<SonarLanguage, AnalyzerArtifacts>> cache = new ConcurrentHashMap<>();
  /** Connection-agnostic; computed once on first access. */
  private Map<String, PluginStatus> embeddedCompanionPlugins;
  /** Companion plugins from the server, keyed by connectionId. */
  private final Map<String, Map<String, PluginStatus>> connectedCompanionCache = new ConcurrentHashMap<>();

  public PluginArtifactProvider(
    StorageService storageService,
    UnsupportedArtifactResolver unsupportedArtifactResolver,
    EmbeddedArtifactResolver embeddedArtifactResolver,
    ConnectedModeArtifactResolver connectedModeArtifactResolver,
    OnDemandArtifactResolver onDemandArtifactResolver,
    PremiumArtifactResolver premiumArtifactResolver,
    EmbeddedExtraArtifactResolver embeddedExtraArtifactResolver,
    ServerPluginsCache serverPluginsCache,
    ApplicationEventPublisher eventPublisher) {
    this.storageService = storageService;
    this.connectedModeArtifactResolver = connectedModeArtifactResolver;
    this.embeddedArtifactResolver = embeddedArtifactResolver;
    this.serverPluginsCache = serverPluginsCache;
    this.eventPublisher = eventPublisher;
    this.resolvers = List.of(
      unsupportedArtifactResolver,
      connectedModeArtifactResolver,
      embeddedArtifactResolver,
      onDemandArtifactResolver,
      premiumArtifactResolver);
    this.extraResolvers = List.of(embeddedExtraArtifactResolver, onDemandArtifactResolver);
  }

  public Map<SonarLanguage, AnalyzerArtifacts> resolve(@Nullable String connectionId) {
    return cache.computeIfAbsent(cacheKey(connectionId), k -> computeResolution(connectionId));
  }

  public boolean arePluginsReady(@Nullable String connectionId) {
    if (resolve(connectionId).values().stream().anyMatch(a -> a.status().state() == ArtifactState.DOWNLOADING)) {
      return false;
    }
    if (connectionId != null) {
      return getConnectedCompanionPlugins(connectionId).values().stream()
        .noneMatch(s -> s.state() == ArtifactState.DOWNLOADING);
    }
    return true;
  }

  public void evict(@Nullable String connectionId) {
    cache.remove(cacheKey(connectionId));
    if (connectionId != null) {
      connectedCompanionCache.remove(connectionId);
    }
  }

  /**
   * Returns all plugin JAR paths for the given connection (or standalone), including
   * language-indexed plugins and companion plugins (those not indexed by any {@link SonarLanguage}).
   */
  public Set<Path> resolveAllPluginJarPaths(@Nullable String connectionId) {
    var paths = new HashSet<Path>();
    resolve(connectionId).values().stream()
      .map(AnalyzerArtifacts::pluginJar).filter(Objects::nonNull).forEach(paths::add);
    getEmbeddedCompanionPlugins().values().stream()
      .map(PluginStatus::path).filter(Objects::nonNull).forEach(paths::add);
    if (connectionId != null) {
      getConnectedCompanionPlugins(connectionId).values().stream()
        .map(PluginStatus::path).filter(Objects::nonNull).forEach(paths::add);
    }
    return paths;
  }

  private Map<String, PluginStatus> getEmbeddedCompanionPlugins() {
    if (embeddedCompanionPlugins == null) {
      embeddedCompanionPlugins = embeddedArtifactResolver.resolveCompanionPlugins(null);
    }
    return embeddedCompanionPlugins;
  }

  public Optional<Path> getConnectedCompanionPath(String connectionId, String pluginKey) {
    return Optional.ofNullable(getConnectedCompanionPlugins(connectionId).get(pluginKey))
      .filter(s -> s.state() == ArtifactState.ACTIVE || s.state() == ArtifactState.SYNCED)
      .map(PluginStatus::path);
  }

  private Map<String, PluginStatus> getConnectedCompanionPlugins(String connectionId) {
    return connectedCompanionCache.computeIfAbsent(connectionId,
      k -> new ConcurrentHashMap<>(connectedModeArtifactResolver.resolveCompanionPlugins(connectionId)));
  }

  private static String cacheKey(@Nullable String connectionId) {
    return Objects.toString(connectionId, STANDALONE);
  }

  private Map<SonarLanguage, AnalyzerArtifacts> computeResolution(@Nullable String connectionId) {
    return Arrays.stream(SonarLanguage.values())
      .collect(Collectors.toConcurrentMap(Function.identity(), language -> resolveForLanguage(language, connectionId)));
  }

  private AnalyzerArtifacts resolveForLanguage(SonarLanguage language, @Nullable String connectionId) {
    for (var resolver : resolvers) {
      var result = resolver.resolve(language, connectionId);
      if (result.isPresent()) {
        var resolved = result.get();
        var status = PluginStatus.forLanguage(language, resolved.state(), resolved.source(), resolved.version(), null, resolved.path());
        if (resolved.state() == ArtifactState.UNSUPPORTED || resolved.state() == ArtifactState.DOWNLOADING) {
          return new AnalyzerArtifacts(status, null, Map.of());
        }
        return resolveWithExtras(language, status, resolved);
      }
    }
    return new AnalyzerArtifacts(PluginStatus.forLanguage(language, ArtifactState.FAILED, null, null, null, null), null, Map.of());
  }

  private AnalyzerArtifacts resolveWithExtras(SonarLanguage language, PluginStatus status, ResolvedArtifact resolved) {
    var extra = new LinkedHashMap<String, Path>();
    if (language == SonarLanguage.CS || language == SonarLanguage.VBNET) {
      for (var key : CSHARP_OMNISHARP_ARTIFACT_KEYS) {
        var path = resolveExtra(key);
        if (path.isEmpty()) {
          return new AnalyzerArtifacts(PluginStatus.forLanguage(language, ArtifactState.FAILED, null, null, null, null), null, Map.of());
        }
        extra.put(key, path.get());
      }
    }
    return new AnalyzerArtifacts(status, resolved.path(), Map.copyOf(extra));
  }

  private Optional<Path> resolveExtra(String key) {
    for (var resolver : extraResolvers) {
      var result = resolver.resolve(key);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }

  public void onSyncComplete(@Nullable String connectionId) {
    if (arePluginsReady(connectionId)) {
      onAllSyncDownloadsComplete(connectionId, computeExpectedPlugins(connectionId));
    }
  }

  private List<ServerPlugin> computeExpectedPlugins(@Nullable String connectionId) {
    if (connectionId == null) {
      return List.of();
    }
    var serverPlugins = serverPluginsCache.refreshAndGet(connectionId);
    if (serverPlugins.isEmpty()) {
      return List.of();
    }
    var serverPluginList = serverPlugins.get();
    var storedPluginsByKey = storageService.connection(connectionId).plugins().getStoredPluginsByKey();
    return computeExpectedInStorage(serverPluginList, storedPluginsByKey);
  }

  private static List<ServerPlugin> computeExpectedInStorage(List<ServerPlugin> serverPlugins, Map<String, StoredPlugin> storedPluginsByKey) {
    return serverPlugins.stream()
      .filter(p -> p.isSonarLintSupported() || storedPluginsByKey.containsKey(p.getKey()))
      .toList();
  }

  private void onAllSyncDownloadsComplete(@Nullable String connectionId, List<ServerPlugin> serverPluginsExpectedInStorage) {
    if (connectionId != null) {
      var pluginsStorage = storageService.connection(connectionId).plugins();
      if (serverPluginsExpectedInStorage.isEmpty()) {
        pluginsStorage.storeNoPlugins();
      } else {
        pluginsStorage.cleanUpUnknownPlugins(serverPluginsExpectedInStorage);
      }
    }
    eventPublisher.publishEvent(new PluginsSynchronizedEvent(connectionId));
  }

  @EventListener
  public void onPluginStatusChanged(PluginStatusUpdateEvent event) {
    var connectionId = event.connectionId();
    event.newStatuses().forEach(status -> updateCachedStatus(connectionId, status));
    onSyncComplete(connectionId);
  }

  private void updateCachedStatus(@Nullable String connectionId, PluginStatus status) {
    if (status.language() != null) {
      var cacheEntry = cache.get(cacheKey(connectionId));
      if (cacheEntry == null) {
        LOG.debug("Can't update plugin status for connection '{}' as no cached status found.", connectionId);
        return;
      }
      var extras = Optional.ofNullable(cacheEntry.get(status.language()))
        .map(AnalyzerArtifacts::extra)
        .orElse(Map.of());
      cacheEntry.put(status.language(), new AnalyzerArtifacts(status, status.path(), extras));
    } else {
      var companionEntry = connectionId != null ? connectedCompanionCache.get(connectionId) : null;
      if (companionEntry == null) {
        LOG.debug("Can't update companion plugin status for connection '{}' as no cached status found.", connectionId);
        return;
      }
      companionEntry.put(status.pluginKey(), status);
    }
  }
}
