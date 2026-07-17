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

import org.sonarsource.sonarlint.core.plugin.PluginsService;

/**
 * Defines how {@link org.sonarsource.sonarlint.core.plugin.source.ArtifactSource ArtifactSource}
 * instances are combined to produce the full set of resolved artifacts for a given context.
 *
 * <p>There are two implementations:
 * <ul>
 *   <li>{@link StandaloneArtifactsLoadingStrategy} — standalone mode, no server connection.</li>
 *   <li>{@link ConnectedArtifactsLoadingStrategy} — connected mode, one instance per connection.</li>
 * </ul>
 *
 * <p>Consumed by {@link PluginsService} to resolve artifacts without knowing the mode.
 * All complexity of listing sources, prioritizing, applying skip-lists, handling enterprise
 * variants and companion plugins stays hidden inside the implementation.</p>
 */
public interface ArtifactsLoadingStrategy {

  /**
   * Selects the effective artifacts without scheduling downloads.
   */
  ArtifactPlan planArtifacts();

}
