/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.clientapi.connection;

import org.sonarsource.sonarlint.core.clientapi.connection.config.ConnectionAddedParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.ConnectionModifiedParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.ConnectionRemovedParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.RegisterConnectionsParams;

/**
 * The client is the source of truth for connection configuration, but the backend also need to be kept in sync.
 * The client will use methods of this service to register existing connection configuration at startup, and then
 * update the service as needed, when a connection configuration is added/removed/updated.
 *
 * One source of complexity for connection configuration is that some attributes (like credentials) should be stored in
 * the IDE secure storage. Accessing secure storage may be delayed after IDE startup, request manual user
 * actions, or even be prevented. So the backend should be able to handle "partial" connection configuration, where
 * credentials are missing.
 *
 */
public interface ConnectionService {

  /**
   * Called by client once at startup, in order to register existing connections to the backend.
   */
  void registerConnections(RegisterConnectionsParams params);

  /**
   * Called by the client when a new connection has been added.
   */
  void connectionAdded(ConnectionAddedParams params);

  /**
   * Called by the client when a connection has been removed.
   */
  void connectionRemoved(ConnectionRemovedParams params);

  /**
   * Called by the client when a connection has been updated.
   */
  void connectionUpdated(ConnectionModifiedParams params);

}
