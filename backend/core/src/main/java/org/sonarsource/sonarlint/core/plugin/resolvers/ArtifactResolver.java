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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.ResolvedArtifact;

/**
 * Defines the contract to resolve (and optionally download) an analyzer artifact for a specific {@link SonarLanguage}.
 * This abstraction allows decoupling the artifact retrieval logic (e.g. embedded plugins, on-demand downloads,
 * or connected mode syncing) from the main plugin loading orchestration.
 */
public interface ArtifactResolver {
  /**
   * Resolves the artifact for the given language. When the result cannot be determined immediately
   * (e.g. a download is required), returns a temporary status and publishes a
   * {@link org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent} with the final result once it is available.
   * Returns empty if this resolver does not handle the given language/connection combination.
   */
  Optional<ResolvedArtifact> resolve(SonarLanguage language, @Nullable String connectionId);
}
