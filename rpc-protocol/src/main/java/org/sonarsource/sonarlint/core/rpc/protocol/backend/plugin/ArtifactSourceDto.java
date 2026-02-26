/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin;

/**
 * Describes where an analyzer plugin artifact was obtained from.
 */
public enum ArtifactSourceDto {

  /** The plugin is bundled with the IDE extension. */
  EMBEDDED,

  /** The plugin was downloaded on demand from an external source (e.g. binaries.sonarsource.com). */
  ON_DEMAND,

  /** The plugin was synchronized from a SonarQube Server connection. */
  SONARQUBE_SERVER,

  /** The plugin was synchronized from a SonarQube Cloud connection. */
  SONARQUBE_CLOUD

}
