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

import java.util.Map;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPluginDependency;

/**
 * Base class for artifact loading strategies. Provides shared filter passes that apply to both
 * standalone and connected mode.
 *
 * <p>Subclasses build a {@code LinkedHashMap<String, ArtifactCandidate>} winner-map using
 * their source-specific priority rules, then call {@link #removeOrphanDependencies} and
 * {@link #removeMissingRequiredDeps} before loading artifacts.</p>
 */
abstract class BaseArtifactsLoadingStrategy implements ArtifactsLoadingStrategy {
  protected BaseArtifactsLoadingStrategy() {
    // only instantiable from subclasses
  }


  /**
   * Removes dependency artifacts whose dependent plugin is not present in the candidate map.
   *
   * <p>A {@link SonarPluginDependency} with no corresponding dependent {@link SonarPlugin}
   * in the map is an orphan and must not be loaded.</p>
   */
  protected static void removeOrphanDependencies(Map<String, ArtifactCandidate> candidates) {
    candidates.entrySet().removeIf(e -> {
      var sonarArtifact = e.getValue().available().sonarArtifact();
      return sonarArtifact.isPresent()
        && sonarArtifact.get() instanceof SonarPluginDependency dependency
        && dependency.getDependents().stream().noneMatch(p -> candidates.containsKey(p.getKey()));
    });
  }

  /**
   * Removes plugins that are missing at least one required (non-optional) dependency in the
   * candidate map.
   */
  protected static void removeMissingRequiredDeps(Map<String, ArtifactCandidate> candidates) {
    candidates.entrySet().removeIf(e -> {
      var sonarArtifact = e.getValue().available().sonarArtifact();
      return sonarArtifact.isPresent()
        && sonarArtifact.get() instanceof SonarPlugin plugin
        && plugin.getDependencies().stream()
          .filter(dep -> !dep.optional())
          .anyMatch(dep -> !candidates.containsKey(dep.artifact().getKey()));
    });
  }
}
