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

import org.sonarsource.sonarlint.core.plugin.source.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;

/**
 * Pairs an {@link AvailableArtifact} with the {@link ArtifactSource} that won the priority
 * contest for its key. Used as the value type of the winner-map in both loading strategies.
 */
record ArtifactCandidate(AvailableArtifact available, ArtifactSource source) {
}
