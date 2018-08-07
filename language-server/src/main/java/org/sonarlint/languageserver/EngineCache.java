/*
 * SonarLint Language Server
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarlint.languageserver;

import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

/**
 * Common interface to create, cache and modify SonarLint engines.
 */
public interface EngineCache {

  /**
   * Get or create and start a standalone engine.
   */
  StandaloneSonarLintEngine getOrCreateStandaloneEngine();

  void stopStandaloneEngine();

  /**
   * Get or create and start a connected engine to the specified server.
   *
   * Returns null if the engine cannot be created.
   */
  @CheckForNull
  ConnectedSonarLintEngine getOrCreateConnectedEngine(ServerInfo serverInfo);

  /**
   * Add extra property. Will apply to newly created engines only.
   */
  void putExtraProperty(String name, String value);

  /**
   * Clear the cache of connected engines, stopping them.
   */
  void clearConnectedEngines();

}
