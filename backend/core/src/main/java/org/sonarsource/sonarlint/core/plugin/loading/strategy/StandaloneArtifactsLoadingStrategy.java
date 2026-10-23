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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.embedded.EmbeddedPluginSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

/**
 * Artifacts loading strategy for standalone (no-connection) mode.
 *
 * <p>Sources, in ascending priority order:
 * <ol>
 *   <li>{@link BinariesArtifactSource} — on-demand downloadable artifacts.</li>
 *   <li>{@link EmbeddedPluginSource} (standalone) — JARs embedded in the IDE client.</li>
 * </ol>
 *
 * <p>Languages available only in connected mode are reported as
 * {@link ArtifactState#PREMIUM} when no other source can provide them.</p>
 */
public class StandaloneArtifactsLoadingStrategy extends BaseArtifactsLoadingStrategy {

  private final InitializeParams params;
  private final BinariesArtifactSource binariesSource;
  private final LanguageSupportRepository languageSupportRepository;
  @Nullable
  private List<ArtifactSource> artifactSourcesSortedByAscendingPriority;

  public StandaloneArtifactsLoadingStrategy(InitializeParams params, BinariesArtifactSource binariesSource, LanguageSupportRepository languageSupportRepository) {
    this.params = params;
    this.binariesSource = binariesSource;
    this.languageSupportRepository = languageSupportRepository;
  }

  private List<ArtifactSource> getArtifactSourcesByAscendingPriority() {
    if (artifactSourcesSortedByAscendingPriority == null) {
      // Ascending priority: binaries < embedded. Later source overwrites for the same key.
      // EmbeddedPluginSource.forStandalone reads JAR manifests and may throw — defer until first use.
      artifactSourcesSortedByAscendingPriority = List.of(binariesSource, EmbeddedPluginSource.forStandalone(params));
    }
    return artifactSourcesSortedByAscendingPriority;
  }

  /**
   * Resolves all artifacts from standalone sources.
   *
   * <p>Priority (highest wins): embedded over binaries. Connected-only languages that
   * cannot be provided by either source are reported as {@link ArtifactState#PREMIUM}.</p>
   */
  @Override
  public ArtifactsLoadingResult resolveArtifacts() {
    var enabledLanguages = languageSupportRepository.getEnabledLanguagesInStandaloneMode();

    // Winner-map: ascending priority, last writer wins per key
    var candidates = new LinkedHashMap<String, ArtifactCandidate>();
    for (var source : getArtifactSourcesByAscendingPriority()) {
      for (var artifact : source.listAvailableArtifacts(enabledLanguages)) {
        candidates.put(artifact.key(), new ArtifactCandidate(artifact, source));
      }
    }

    // remove base keys superseded by a different-key enterprise variant
    new ArrayList<>(candidates.keySet()).stream()
      .filter(SonarPlugin::isEnterpriseVariant)
      .forEach(entKey -> SonarPlugin.baseKeyFor(entKey).ifPresent(candidates::remove));

    removeOrphanDependencies(candidates);
    removeMissingRequiredDeps(candidates);

    // Group winning keys by source, then load once per source
    var keysBySource = new HashMap<ArtifactSource, HashSet<String>>();
    candidates.forEach((key, candidate) -> keysBySource.computeIfAbsent(candidate.source(), s -> new HashSet<>()).add(key));
    var result = new LinkedHashMap<String, ResolvedArtifact>();
    keysBySource.forEach((source, keys) -> result.putAll(source.load(keys).resolvedArtifactsByKey()));

    // For each language not yet resolved and available only in connected mode, mark PREMIUM
    for (var language : SonarLanguage.values()) {
      var key = language.getPlugin().getKey();
      if (!result.containsKey(key) && languageSupportRepository.isEnabledOnlyInConnectedMode(language)) {
        result.put(key, ResolvedArtifact.premium());
      }
    }

    return new ArtifactsLoadingResult(enabledLanguages, result);
  }
}
