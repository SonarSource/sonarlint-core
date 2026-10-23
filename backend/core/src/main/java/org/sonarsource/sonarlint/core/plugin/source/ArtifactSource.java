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
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.embedded.EmbeddedPluginSource;
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginSource;

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
 *   <li>{@link ServerPluginSource} — artifacts synchronized from a connected server.</li>
 * </ul>
 *
 * <p>The two methods follow a list-then-act pattern:
 * <ul>
 *   <li>{@link #listAvailableArtifacts(Set)} is a pure query — no side effects, no downloads.</li>
 *   <li>{@link #load(Set)} is the action — given the full set of artifact keys that this source
 *       won in the priority contest, it ensures each artifact is available, scheduling background
 *       downloads when necessary. Receiving the complete set at once allows implementations (in
 *       particular {@link ServerPluginSource}) to take storage-level actions that require knowing
 *       all winners upfront (e.g. writing empty reference files for server plugins that were not
 *       selected). Keys absent from the returned {@link LoadResult} are silently ignored by the
 *       caller.</li>
 * </ul>
 */
public interface ArtifactSource {

  /**
   * Returns all artifacts known to this source for the given set of enabled languages, without triggering any downloads. This is a pure query.
   * Implementations should return artifacts corresponding to enabled languages, and artifacts that are not tied to a specific language.
   */
  List<AvailableArtifact> listAvailableArtifacts(Set<SonarLanguage> enabledLanguages);

  /**
   * Ensures every artifact in {@code artifactKeys} is available from this source, scheduling
   * background downloads when necessary. {@code artifactKeys} is the complete set of keys that
   * this source won in the priority contest for the current load cycle, allowing implementations
   * to reason about the full picture at once. A key may be absent from the returned
   * {@link LoadResult} if this source cannot provide it. Resolved artifacts may carry the state
   * {@link ArtifactState#DOWNLOADING} when a background download has been scheduled.
   */
  LoadResult load(Set<String> artifactKeys);
}
