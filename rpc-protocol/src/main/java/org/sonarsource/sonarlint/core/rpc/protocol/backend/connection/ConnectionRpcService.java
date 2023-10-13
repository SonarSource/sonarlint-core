/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.connection;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
<<<<<<<< HEAD:rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionRpcService.java
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
========
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionService.java
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsResponse;
<<<<<<<< HEAD:rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionRpcService.java
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse;
========
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionService.java
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
<<<<<<<< HEAD:rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionRpcService.java

/**
 * The client is the source of truth for connection configuration, but the backend also need to be kept in sync.
 * The client will use {@link SonarLintRpcServer#initialize(InitializeParams)} to register existing connection configurations at startup, and then
========
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintBackend;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintClient;

/**
 * The client is the source of truth for connection configuration, but the backend also need to be kept in sync.
 * The client will use {@link SonarLintBackend#initialize(InitializeParams)} to register existing connection configurations at startup, and then
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionService.java
 * update the service as needed using {@link #didUpdateConnections(DidUpdateConnectionsParams)}, when a connection configuration is added/removed/updated.
 *
 * One source of complexity for connection configuration is that some attributes (like credentials) should be stored in
 * the IDE secure storage. Accessing secure storage may be delayed after IDE startup, request manual user
 * actions, or even be prevented. So the backend should be able to handle "partial" connection configuration, where
 * credentials are missing.
 */
@JsonSegment("connection")
<<<<<<<< HEAD:rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionRpcService.java
public interface ConnectionRpcService {
========
public interface ConnectionService {
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionService.java

  /**
   * Called by the client when connection configurations have been changed.
   */
  @JsonNotification
  void didUpdateConnections(DidUpdateConnectionsParams params);

  /**
   * Called by the client when connection credentials have been changed. The backend might later retrieve them with
<<<<<<<< HEAD:rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionRpcService.java
   * {@link SonarLintRpcClient#getCredentials(GetCredentialsParams)}.
========
   * {@link SonarLintClient#getCredentials(GetCredentialsParams)}.
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/connection/ConnectionService.java
   */
  @JsonNotification
  void didChangeCredentials(DidChangeCredentialsParams params);

  /**
   * @param params url of the server on which to create the token
   * @return For servers that support automatic token generation, will return the token in the response. Else no token will be returned.
   * If the local server is not started or the server URL can not be reached, the future will fail
   */
  @JsonRequest
  CompletableFuture<HelpGenerateUserTokenResponse> helpGenerateUserToken(HelpGenerateUserTokenParams params);

  /**
   * Validate that connection is valid:
   * <ul>
   * <li>check that the server is reachable</li>
   * <li>check that the server minimal version is satisfied</li>
   * <li>check that the credentials are valid</li>
   * <li>check that the organization exists (for SonarCloud)</li>
   * </ul>
   */
  @JsonRequest
  CompletableFuture<ValidateConnectionResponse> validateConnection(ValidateConnectionParams params);

  /**
   * Check that smart notifications are supported by the server by sending a GET request to /api/developers/search_events?projects=&from=
   * It is successfully when response code is >= 200 and < 300
   */
  @JsonRequest
  CompletableFuture<CheckSmartNotificationsSupportedResponse> checkSmartNotificationsSupported(CheckSmartNotificationsSupportedParams params);

  @JsonRequest
  CompletableFuture<ListUserOrganizationsResponse> listUserOrganizations(ListUserOrganizationsParams params);

  /**
   * Find an organization by key. If not found the response will contain null.
   */
  @JsonRequest
  CompletableFuture<GetOrganizationResponse> getOrganization(GetOrganizationParams params);

  /**
   * Get all projects existing on SonarQube or in a SonarCloud organization.
   * As this data might be needed during connection creation, it accepts a transient connection.
   */
  @JsonRequest
  CompletableFuture<GetAllProjectsResponse> getAllProjects(GetAllProjectsParams params);

}
