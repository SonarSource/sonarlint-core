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
package org.sonarsource.sonarlint.core.plugin.source;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.embedded.EmbeddedPluginSource;

/**
 * Represents one of the origins from which artifacts (plugins and plugin dependencies) can be
 * obtained: the client (embedded), public binaries on-demand, or a SonarQube/SonarQube Cloud
 * server (connected mode).
 *
 * <p>There are three concrete implementations:
 * <ul>
 *   <li>{@link EmbeddedPluginSource} — JARs bundled in the IDE extension.</li>
 *   <li>{@link BinariesArtifactSource} — artifacts downloadable from
 *       binaries.sonarsource.com.</li>
 *   <li>ServerPluginSource — artifacts synchronized from a connected server.</li>
 * </ul>
 *
 * <p>The two methods follow a list-then-act pattern:
 * <ul>
 *   <li>{@link #listAvailableArtifacts(Set)} is a pure query — no side effects, no downloads.</li>
 *   <li>{@link #load(String)} is the action — it ensures the artifact is available, scheduling a
 *       background download when necessary.</li>
 * </ul>
 */
public interface ArtifactSource {

  /**
   * Returns all artifacts known to this source for the given set of enabled languages, without triggering any downloads. This is a pure query.
   * Implementations should return artifacts corresponding to enabled languages, and artifacts that are not tied to a specific language.
   */
  List<AvailableArtifact> listAvailableArtifacts(Set<SonarLanguage> enabledLanguages);

  /**
   * Ensures the artifact identified by {@code key} is available from this source,
   * scheduling a background download if needed. Returns empty if this source does not handle the
   * given key. May return a {@link ResolvedArtifact} in
   * {@link ArtifactState#DOWNLOADING} state.
   */
  Optional<ResolvedArtifact> load(String artifactKey);
}
