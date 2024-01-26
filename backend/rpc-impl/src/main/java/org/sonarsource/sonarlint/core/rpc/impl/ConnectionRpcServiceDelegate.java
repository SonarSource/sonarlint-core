/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.ConnectionService;
import org.sonarsource.sonarlint.core.OrganizationsCache;
import org.sonarsource.sonarlint.core.SonarProjectsCache;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.FuzzySearchUserOrganizationsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.FuzzySearchUserOrganizationsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.FuzzySearchProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.FuzzySearchProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;

class ConnectionRpcServiceDelegate extends AbstractRpcServiceDelegate implements ConnectionRpcService {

  public ConnectionRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public void didUpdateConnections(DidUpdateConnectionsParams params) {
    notify(() -> getBean(ConnectionService.class).didUpdateConnections(params.getSonarQubeConnections(), params.getSonarCloudConnections()));
  }

  @Override
  public void didChangeCredentials(DidChangeCredentialsParams params) {
    notify(() -> getBean(ConnectionService.class).didChangeCredentials(params.getConnectionId()));
  }

  @Override
  public CompletableFuture<HelpGenerateUserTokenResponse> helpGenerateUserToken(HelpGenerateUserTokenParams params) {
    return requestAsync(cancelMonitor -> getBean(ConnectionService.class).helpGenerateUserToken(params.getServerUrl(), params.isSonarCloud(), cancelMonitor));
  }

  @Override
  public CompletableFuture<ValidateConnectionResponse> validateConnection(ValidateConnectionParams params) {
    return requestAsync(cancelMonitor -> getBean(ConnectionService.class).validateConnection(params.getTransientConnection(), cancelMonitor));
  }

  @Override
  public CompletableFuture<CheckSmartNotificationsSupportedResponse> checkSmartNotificationsSupported(CheckSmartNotificationsSupportedParams params) {
    return requestAsync(
      cancelMonitor -> new CheckSmartNotificationsSupportedResponse(getBean(ConnectionService.class)
        .checkSmartNotificationsSupported(params.getTransientConnection(), cancelMonitor)));
  }

  @Override
  public CompletableFuture<ListUserOrganizationsResponse> listUserOrganizations(ListUserOrganizationsParams params) {
    return requestAsync(cancelMonitor -> new ListUserOrganizationsResponse(getBean(OrganizationsCache.class).listUserOrganizations(params.getCredentials(), cancelMonitor)));
  }

  @Override
  public CompletableFuture<GetOrganizationResponse> getOrganization(GetOrganizationParams params) {
    return requestAsync(cancelMonitor -> new GetOrganizationResponse(getBean(OrganizationsCache.class)
      .getOrganization(params.getCredentials(), params.getOrganizationKey(), cancelMonitor)));
  }

  @Override
  public CompletableFuture<FuzzySearchUserOrganizationsResponse> fuzzySearchUserOrganizations(FuzzySearchUserOrganizationsParams params) {
    return requestAsync(cancelMonitor -> new FuzzySearchUserOrganizationsResponse(getBean(OrganizationsCache.class)
      .fuzzySearchOrganizations(params.getCredentials(), params.getSearchText(), cancelMonitor)));
  }

  @Override
  public CompletableFuture<GetAllProjectsResponse> getAllProjects(GetAllProjectsParams params) {
    return requestAsync(cancelMonitor -> new GetAllProjectsResponse(getBean(ConnectionService.class).getAllProjects(params.getTransientConnection(), cancelMonitor)));
  }

  @Override
  public CompletableFuture<FuzzySearchProjectsResponse> fuzzySearchProjects(FuzzySearchProjectsParams params) {
    return requestAsync(cancelMonitor -> new FuzzySearchProjectsResponse(getBean(SonarProjectsCache.class)
      .fuzzySearchProjects(params.getConnectionId(), params.getSearchText(), cancelMonitor)));
  }
}
