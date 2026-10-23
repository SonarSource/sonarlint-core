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

import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.plugins.SonarArtifact;

/**
 * An artifact (plugin or plugin dependency) known to a given {@link ArtifactSource}.
 * Returned by {@link ArtifactSource#listAvailableArtifacts(Set)} as a pure query with no side
 * effects.
 *
 * <p>{@code isEnterprise} is {@code true} when the artifact is the enterprise edition of a
 * plugin on the current connection. Enterprise artifacts take priority over embedded sources
 * in {@code ConnectedArtifactsLoadingStrategy}.</p>
 */
public record AvailableArtifact(String key, @Nullable Version version, boolean isEnterprise, Optional<? extends SonarArtifact> sonarArtifact) {
}
