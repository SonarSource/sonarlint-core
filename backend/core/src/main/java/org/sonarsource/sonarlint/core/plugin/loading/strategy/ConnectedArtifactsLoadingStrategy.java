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
package org.sonarsource.sonarlint.core.plugin.loading.strategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.embedded.EmbeddedPluginSource;
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

/**
 * Artifacts loading strategy for connected mode (a specific connection).
 *
 * <p>One instance is created per connection and cached by
 * {@link ConnectedArtifactsLoadingStrategyFactory}.</p>
 *
 * <p>Sources, in ascending priority order:
 * <ol>
 *   <li>{@link BinariesArtifactSource} — on-demand downloadable artifacts (fallback).</li>
 *   <li>{@link ServerPluginSource} — artifacts synced from the server.</li>
 *   <li>{@link EmbeddedPluginSource} (connected) — JARs embedded in the IDE client (highest
 *       priority in normal circumstances).</li>
 * </ol>
 *
 * <p>{@link #planArtifacts()} uses a winner-map pattern: iterate sources in ascending
 * priority, last writer wins per key, then apply passes to correct the map before loading.
 *
 * <p>Connected-mode-specific passes (applied before the shared passes):
 * <ol>
 *   <li><b>Enterprise-variant deduplication</b>: when a different-key enterprise variant
 *       ({@code csharpenterprise}, {@code vbnetenterprise}) is present, the base key is removed
 *       so both are not loaded simultaneously.</li>
 *   <li><b>Enterprise priority override</b>: when the server reports a plugin as enterprise
 *       ({@link AvailableArtifact#isEnterprise()}), that plugin is forced to use the server
 *       source even if the embedded source would normally win. This applies to same-key
 *       enterprise plugins (GO, IAC) whose enterprise edition is served when the
 *       connection qualifies (SonarQube Server &ge; minimum version, or SonarQube Cloud).</li>
 * </ol>
 */
public class ConnectedArtifactsLoadingStrategy extends BaseArtifactsLoadingStrategy {
  private final ServerPluginSource serverSource;
  private final ArtifactStorageCleaner storageCleaner;
  private final LanguageSupportRepository languageSupportRepository;
  private final List<ArtifactSource> artifactSourcesSortedByAscendingPriority;

  ConnectedArtifactsLoadingStrategy(InitializeParams params, BinariesArtifactSource binariesSource,
    ServerPluginSource serverSource, LanguageSupportRepository languageSupportRepository) {
    this(params, binariesSource, serverSource, languageSupportRepository, selectedArtifactKeys -> {
    });
  }

  ConnectedArtifactsLoadingStrategy(InitializeParams params, BinariesArtifactSource binariesSource,
    ServerPluginSource serverSource, LanguageSupportRepository languageSupportRepository, ArtifactStorageCleaner storageCleaner) {
    this.serverSource = serverSource;
    this.storageCleaner = storageCleaner;
    this.languageSupportRepository = languageSupportRepository;
    // Ascending priority: binaries (fallback) → server → embedded (highest)
    this.artifactSourcesSortedByAscendingPriority = List.of(
      binariesSource,
      serverSource,
      EmbeddedPluginSource.forConnected(params));
  }

  /**
   * Plans all artifacts from all sources using a winner-map pattern without starting downloads.
   *
   * <p>Priority (highest wins in normal cases): embedded &gt; server &gt; binaries.
   * Exception: enterprise server plugins beat embedded (see class Javadoc).</p>
   */
  @Override
  public ArtifactPlan planArtifacts() {
    var enabledLanguages = languageSupportRepository.getEnabledLanguagesInConnectedMode();
    var candidates = selectArtifacts(enabledLanguages);
    cleanStorage(candidates);
    return new ArtifactPlan(enabledLanguages, candidates.entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().available(), (left, right) -> right, LinkedHashMap::new)), Map.of());
  }

  private LinkedHashMap<String, ArtifactCandidate> selectArtifacts(Set<SonarLanguage> enabledLanguages) {

    // Query server artifacts once; reused in normal pass and enterprise-override pass
    var serverArtifacts = serverSource.listAvailableArtifacts(enabledLanguages);

    // Winner-map: ascending priority, last writer wins per key
    var candidates = new LinkedHashMap<String, ArtifactCandidate>();
    for (var source : artifactSourcesSortedByAscendingPriority) {
      var artifacts = (source == serverSource) ? serverArtifacts : source.listAvailableArtifacts(enabledLanguages);
      for (var artifact : artifacts) {
        candidates.put(artifact.key(), new ArtifactCandidate(artifact, source));
      }
    }

    // Pass 1 (connected-specific): remove base keys superseded by a different-key enterprise variant
    new ArrayList<>(candidates.keySet()).stream()
      .filter(SonarPlugin::isEnterpriseVariant)
      .forEach(entKey -> SonarPlugin.baseKeyFor(entKey).ifPresent(candidates::remove));

    // Pass 2 (connected-specific): enterprise server plugins override even embedded
    serverArtifacts.stream()
      .filter(AvailableArtifact::isEnterprise)
      .forEach(artifact -> candidates.computeIfPresent(artifact.key(), (key, existing) -> new ArtifactCandidate(artifact, serverSource)));

    // Shared passes
    removeOrphanDependencies(candidates);
    removeMissingRequiredDeps(candidates);

    return candidates;
  }

  private void cleanStorage(Map<String, ArtifactCandidate> candidates) {
    storageCleaner.clean(candidates.entrySet().stream()
      .filter(entry -> entry.getValue().source() == serverSource)
      .map(Map.Entry::getKey)
      .collect(Collectors.toSet()));
  }
}
