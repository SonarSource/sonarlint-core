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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPluginDependency;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;

public record ArtifactsLoadingResult(Set<SonarLanguage> enabledLanguages, Map<String, ResolvedArtifact> resolvedArtifactsByKey) {

  public Optional<ResolvedArtifact> getResolvedArtifactByKey(String key) {
    return Optional.ofNullable(resolvedArtifactsByKey().get(key));
  }

  /**
   * All artifacts must not be loaded, only real Sonar plugins.
   */
  public List<Path> getPluginPaths() {
    var pluginPaths = new ArrayList<Path>();
    for (var entry : resolvedArtifactsByKey.entrySet()) {
      var key = entry.getKey();
      var artifact = entry.getValue();
      // only load artifacts that are ready on disk and not dependencies
      if (artifact == null || artifact.path() == null || SonarPluginDependency.findByKey(key).isPresent()) {
        continue;
      }
      // only load plugins whose required dependencies are also present on disk
      if (areRequiredDependenciesPresent(key)) {
        pluginPaths.add(artifact.path());
      }
    }
    return pluginPaths;
  }

  private boolean areRequiredDependenciesPresent(String key) {
    return SonarPlugin.findByKey(key)
      .map(plugin -> plugin.getDependencies().stream()
        .filter(dep -> !dep.optional())
        .allMatch(dep -> {
          var depArtifact = resolvedArtifactsByKey.get(dep.artifact().getKey());
          return depArtifact != null && depArtifact.path() != null;
        }))
      .orElse(true);
  }

  public Optional<CompletableFuture<Void>> getAllDownloadsFuture() {
    var pendingDownloads = resolvedArtifactsByKey.values().stream().map(ResolvedArtifact::downloadFuture)
      .filter(Objects::nonNull)
      .toList();
    if (pendingDownloads.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(CompletableFuture.allOf(pendingDownloads.toArray(new CompletableFuture[0])));
  }

  public void whenAllArtifactsDownloaded(Runnable runnable) {
    getAllDownloadsFuture()
      .ifPresent(future -> {
        var logOutput = SonarLintLogger.get().getTargetForCopy();
        future.thenRun(() -> {
          SonarLintLogger.get().setTarget(logOutput);
          runnable.run();
        });
      });
  }
}
