/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// ServerConnections should be shared between engines and the new backend, e.g. to manage concurrent access
// it's all static because there is no common entrypoint for engines and the backend
// it is a temporary solution, when engines will be removed, the cache will still be needed but the static access can be removed
public class ServerConnectionCache {

  private static final Map<ServerConnectionSpec, ServerConnection> connectionsBySpec = new ConcurrentHashMap<>();

  public static ServerConnection getOrCreate(ServerConnectionSpec spec) {
    return connectionsBySpec.computeIfAbsent(spec, k -> new ServerConnection(spec));
  }

  private ServerConnectionCache() {
    // singleton
  }

  public static synchronized void clear() {
    connectionsBySpec.values().forEach(connection -> connection.stop(false));
    connectionsBySpec.clear();
  }
}
