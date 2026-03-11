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
import java.util.Collections;
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
import org.sonarsource.sonarlint.core.event.PluginStatusChangedEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
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
 *   <li>{@link OnDemandArtifactResolver} — standalone-only on-demand download (e.g. CFamily).</li>
 *   <li>{@link PremiumArtifactResolver} — sentinel for languages that require a connected-mode
 *       server but are not available locally.</li>
 * </ol>
 */
@Component
public class PluginArtifactProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String STANDALONE = "STANDALONE";
  private static final String GO_ENTERPRISE_PLUGIN_KEY = "goenterprise";
  private static final String IAC_ENTERPRISE_PLUGIN_KEY = "iacenterprise";
  // Legacy standalone TypeScript plugin, superseded by the JavaScript plugin
  private static final String OLD_TYPESCRIPT_PLUGIN_KEY = "typescript";

  private static final Set<String> CSHARP_OMNISHARP_ARTIFACT_KEYS = Set.of(
    DownloadableArtifact.OMNISHARP_MONO.artifactKey(),
    DownloadableArtifact.OMNISHARP_NET6.artifactKey(),
    DownloadableArtifact.OMNISHARP_WIN.artifactKey());

  private final List<ArtifactResolver> resolvers;
  private final List<ExtraArtifactResolver> extraResolvers;
  private final LanguageSupportRepository languageSupportRepository;
  private final ConnectedModeArtifactResolver connectedModeArtifactResolver;
  private final EmbeddedArtifactResolver embeddedArtifactResolver;
  private final ServerPluginsCache serverPluginsCache;
  private final StorageService storageService;
  private final ApplicationEventPublisher eventPublisher;
  private final Set<String> notSonarLintSupportedKeysToSynchronize;
  private final Map<String, Map<SonarLanguage, AnalyzerArtifacts>> cache = new ConcurrentHashMap<>();

  public PluginArtifactProvider(
    StorageService storageService,
    LanguageSupportRepository languageSupportRepository,
    UnsupportedArtifactResolver unsupportedArtifactResolver,
    EmbeddedArtifactResolver embeddedArtifactResolver,
    ConnectedModeArtifactResolver connectedModeArtifactResolver,
    OnDemandArtifactResolver onDemandArtifactResolver,
    PremiumArtifactResolver premiumArtifactResolver,
    EmbeddedExtraArtifactResolver embeddedExtraArtifactResolver,
    ServerPluginsCache serverPluginsCache,
    ApplicationEventPublisher eventPublisher) {
    this.storageService = storageService;
    this.languageSupportRepository = languageSupportRepository;
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
    this.notSonarLintSupportedKeysToSynchronize = computeNotSonarLintSupportedKeysToSynchronize(languageSupportRepository.getEnabledLanguagesInConnectedMode());
  }

  private static Set<String> computeNotSonarLintSupportedKeysToSynchronize(Set<SonarLanguage> enabledLanguages) {
    var keys = new HashSet<String>();
    if (enabledLanguages.contains(SonarLanguage.GO)) {
      // SLCORE-1337 Force synchronize "Go Enterprise" before proper repackaging (SQS 2025.2)
      keys.add(GO_ENTERPRISE_PLUGIN_KEY);
    }
    if (enabledLanguages.contains(SonarLanguage.ANSIBLE) || enabledLanguages.contains(SonarLanguage.GITHUBACTIONS)) {
      // Force synchronize "IAC Enterprise" for servers before  proper repackaging (SQ 2025.6)
      keys.add(IAC_ENTERPRISE_PLUGIN_KEY);
    }
    if (enabledLanguages.contains(SonarLanguage.CS)) {
      // SLCORE-1179 Force synchronize "C# Enterprise" after repackaging (SQS 10.8+)
      keys.add(ConnectedModeArtifactResolver.CSHARP_ENTERPRISE_PLUGIN_KEY);
      // SLCORE-1898 Synchronize of OSS plugins for dotnet in connected mode, should be removed with SLVS-2778
      keys.add(ConnectedModeArtifactResolver.CSHARP_OSS_PLUGIN_KEY);
    }
    if (enabledLanguages.contains(SonarLanguage.VBNET)) {
      // SLCORE-1179 Force synchronize "VB.NET Enterprise" after repackaging (SQS 10.8+)
      keys.add(ConnectedModeArtifactResolver.VBNET_ENTERPRISE_PLUGIN_KEY);
      // SLCORE-1898 Synchronize of OSS plugins for dotnet in connected mode, should be removed with SLVS-2778
      keys.add(ConnectedModeArtifactResolver.VBNET_OSS_PLUGIN_KEY);
    }
    return Collections.unmodifiableSet(keys);
  }

  public Map<SonarLanguage, AnalyzerArtifacts> resolve(@Nullable String connectionId) {
    return cache.computeIfAbsent(cacheKey(connectionId), k -> computeResolution(connectionId));
  }

  public boolean arePluginsReady(@Nullable String connectionId) {
    return resolve(connectionId).values().stream()
      .noneMatch(a -> a.status().state() == ArtifactState.DOWNLOADING);
  }

  public void evict(@Nullable String connectionId) {
    cache.remove(cacheKey(connectionId));
  }

  public Set<Path> getAdditionalEmbeddedPluginPaths() {
    return embeddedArtifactResolver.getAdditionalPluginPaths();
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
      var result = resolver.resolveAsync(language, connectionId);
      if (result.isPresent()) {
        var resolved = result.get();
        var status = new PluginStatus(language, resolved.state(), resolved.source(), resolved.version(), null, resolved.path());
        if (resolved.state() == ArtifactState.UNSUPPORTED || resolved.state() == ArtifactState.DOWNLOADING) {
          return new AnalyzerArtifacts(status, null, Map.of());
        }
        return resolveWithExtras(language, status, resolved);
      }
    }
    return new AnalyzerArtifacts(new PluginStatus(language, ArtifactState.FAILED, null, null, null, null), null, Map.of());
  }

  private AnalyzerArtifacts resolveWithExtras(SonarLanguage language, PluginStatus status, ResolvedArtifact resolved) {
    var extra = new LinkedHashMap<String, Path>();
    if (language == SonarLanguage.CS || language == SonarLanguage.VBNET) {
      for (var key : CSHARP_OMNISHARP_ARTIFACT_KEYS) {
        var path = resolveExtra(key);
        if (path.isEmpty()) {
          return new AnalyzerArtifacts(new PluginStatus(language, ArtifactState.FAILED, null, null, null, null), null, Map.of());
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

  /**
   * Fetches the server plugin list, determines which plugins are absent or outdated, and
   * synchronously downloads them. Fires {@link PluginsSynchronizedEvent} when all downloads
   * for this connection complete.
   */
  public void syncConnectionPlugins(String connectionId) {
    var serverPlugins = serverPluginsCache.refreshAndGet(connectionId);
    if (serverPlugins.isEmpty()) {
      return;
    }
    var serverPluginList = serverPlugins.get();
    var storedPluginsByKey = storageService.connection(connectionId).plugins().getStoredPluginsByKey();
    var serverPluginsExpectedInStorage = computeExpectedInStorage(serverPluginList, storedPluginsByKey);

    var enabledLanguages = languageSupportRepository.getEnabledLanguagesInConnectedMode();
    var enabledPluginKeys = enabledLanguages.stream().map(SonarLanguage::getPluginKey).collect(Collectors.toSet());
    var disabledPluginKeys = computeDisabledPluginKeys(enabledLanguages);

    enabledLanguages.forEach(language -> resolveSync(language, connectionId));

    serverPluginList.stream()
      .filter(plugin -> shouldSyncPlugin(plugin, storedPluginsByKey, enabledPluginKeys, disabledPluginKeys))
      .forEach(plugin -> connectedModeArtifactResolver.downloadPluginSync(connectionId, plugin));

    logSkips(serverPluginList, disabledPluginKeys);

    onAllSyncDownloadsComplete(connectionId, serverPluginsExpectedInStorage);
  }

  private void logSkips(List<ServerPlugin> serverPluginList, Set<String> disabledPluginKeys) {
    serverPluginList.stream()
      .filter(plugin -> !plugin.isSonarLintSupported() && !notSonarLintSupportedKeysToSynchronize.contains(plugin.getKey()))
      .forEach(plugin -> LOG.debug("[SYNC] Code analyzer '{}' does not support SonarLint. Skip downloading it.", plugin.getKey()));

    serverPluginList.stream()
      .filter(ServerPlugin::isSonarLintSupported)
      .filter(plugin -> !notSonarLintSupportedKeysToSynchronize.contains(plugin.getKey()))
      .filter(plugin -> disabledPluginKeys.contains(plugin.getKey()))
      .forEach(plugin -> LOG.debug("[SYNC] Code analyzer '{}' is disabled in SonarLint (language not enabled). Skip downloading it.", plugin.getKey()));
  }

  private boolean shouldSyncPlugin(ServerPlugin plugin, Map<String, StoredPlugin> storedPluginsByKey,
    Set<String> enabledPluginKeys, Set<String> disabledPluginKeys) {
    if (!isOutdatedOrAbsent(plugin, storedPluginsByKey)) {
      return false;
    }
    if (notSonarLintSupportedKeysToSynchronize.contains(plugin.getKey())) {
      return true;
    }
    return plugin.isSonarLintSupported()
      && !enabledPluginKeys.contains(plugin.getKey())
      && !disabledPluginKeys.contains(plugin.getKey());
  }

  private static Set<String> computeDisabledPluginKeys(Set<SonarLanguage> enabledLanguages) {
    var languagesByPluginKey = Arrays.stream(SonarLanguage.values())
      .collect(Collectors.groupingBy(SonarLanguage::getPluginKey));
    var disabled = languagesByPluginKey.entrySet().stream()
      .filter(e -> Collections.disjoint(enabledLanguages, e.getValue()))
      .map(Map.Entry::getKey)
      .collect(Collectors.toCollection(HashSet::new));
    if (!enabledLanguages.contains(SonarLanguage.TS)) {
      disabled.add(OLD_TYPESCRIPT_PLUGIN_KEY);
    }
    return disabled;
  }

  private void resolveSync(SonarLanguage language, String connectionId) {
    for (var resolver : resolvers) {
      var result = resolver.resolve(language, connectionId);
      if (result.isPresent()) {
        return;
      }
    }
  }

  private static boolean isOutdatedOrAbsent(ServerPlugin serverPlugin, Map<String, StoredPlugin> storedPluginsByKey) {
    var stored = storedPluginsByKey.get(serverPlugin.getKey());
    return stored == null || !stored.hasSameHash(serverPlugin);
  }

  private static List<ServerPlugin> computeExpectedInStorage(List<ServerPlugin> serverPlugins, Map<String, StoredPlugin> storedPluginsByKey) {
    return serverPlugins.stream()
      .filter(p -> p.isSonarLintSupported() || storedPluginsByKey.containsKey(p.getKey()))
      .toList();
  }

  private void onAllSyncDownloadsComplete(String connectionId, List<ServerPlugin> serverPluginsExpectedInStorage) {
    var pluginsStorage = storageService.connection(connectionId).plugins();
    if (serverPluginsExpectedInStorage.isEmpty()) {
      pluginsStorage.storeNoPlugins();
    } else {
      pluginsStorage.cleanUpUnknownPlugins(serverPluginsExpectedInStorage);
    }
    eventPublisher.publishEvent(new PluginsSynchronizedEvent(connectionId));
  }

  @EventListener
  public void onPluginStatusChanged(PluginStatusChangedEvent event) {
    var connectionId = event.connectionId();
    var cacheEntry = cache.get(cacheKey(connectionId));
    if (cacheEntry == null) {
      LOG.debug("Can't update plugin status for connection '{}' as no cached status found.", connectionId);
      return;
    }
    for (var status : event.newStatuses()) {
      var extras = Optional.ofNullable(cacheEntry.get(status.language()))
        .map(AnalyzerArtifacts::extra)
        .orElse(Map.of());
      cacheEntry.put(status.language(), new AnalyzerArtifacts(status, status.path(), extras));
    }
  }
}
