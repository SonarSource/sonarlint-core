/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.connection;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;

/**
 * The client is the source of truth for connection configuration, but the backend also need to be kept in sync.
 * The client will use {@link org.sonarsource.sonarlint.core.clientapi.SonarLintBackend#initialize(InitializeParams)} to register existing connection configurations at startup, and then
 * update the service as needed using {@link #didUpdateConnections(DidUpdateConnectionsParams)}, when a connection configuration is added/removed/updated.
 *
 * One source of complexity for connection configuration is that some attributes (like credentials) should be stored in
 * the IDE secure storage. Accessing secure storage may be delayed after IDE startup, request manual user
 * actions, or even be prevented. So the backend should be able to handle "partial" connection configuration, where
 * credentials are missing.
 *
 */
public interface ConnectionService {

  /**
   * Called by the client when connection configurations have been changed.
   */
  @JsonNotification
  void didUpdateConnections(DidUpdateConnectionsParams params);

}
