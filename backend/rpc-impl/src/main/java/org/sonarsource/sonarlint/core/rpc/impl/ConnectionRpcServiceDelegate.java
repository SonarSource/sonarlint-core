/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.ConnectionService;
import org.sonarsource.sonarlint.core.ConnectionSuggestionProvider;
import org.sonarsource.sonarlint.core.MCPServerSettingsProvider;
import org.sonarsource.sonarlint.core.OrganizationsCache;
import org.sonarsource.sonarlint.core.SonarProjectsCache;
import org.sonarsource.sonarlint.core.commons.SonarLintException;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.GetConnectionSuggestionsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.GetMCPServerSettingsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.GetMCPServerSettingsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetConnectionSuggestionsParams;

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
    return requestAsync(cancelMonitor -> getBean(ConnectionService.class).helpGenerateUserToken(params.getServerUrl(), params.getUtm(), cancelMonitor));
  }

  @Override
  public CompletableFuture<ValidateConnectionResponse> validateConnection(ValidateConnectionParams params) {
    return requestAsync(cancelMonitor -> getBean(ConnectionService.class).validateConnection(params.getTransientConnection(), cancelMonitor));
  }

  @Override
  public CompletableFuture<ListUserOrganizationsResponse> listUserOrganizations(ListUserOrganizationsParams params) {
    return requestAsync(cancelMonitor -> new ListUserOrganizationsResponse(getBean(OrganizationsCache.class)
      .listUserOrganizations(new TransientSonarCloudConnectionDto(null, params.getCredentials(), params.getRegion()), cancelMonitor)));
  }

  @Override
  public CompletableFuture<GetOrganizationResponse> getOrganization(GetOrganizationParams params) {
    return requestAsync(cancelMonitor -> new GetOrganizationResponse(getBean(OrganizationsCache.class)
      .getOrganization(new TransientSonarCloudConnectionDto(params.getOrganizationKey(), params.getCredentials(), params.getRegion()), cancelMonitor)));
  }

  @Override
  public CompletableFuture<FuzzySearchUserOrganizationsResponse> fuzzySearchUserOrganizations(FuzzySearchUserOrganizationsParams params) {
    return requestAsync(cancelMonitor -> new FuzzySearchUserOrganizationsResponse(getBean(OrganizationsCache.class)
      .fuzzySearchOrganizations(new TransientSonarCloudConnectionDto(null, params.getCredentials(), params.getRegion()), params.getSearchText(), cancelMonitor)));
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

  @Override
  public CompletableFuture<GetProjectNamesByKeyResponse> getProjectNamesByKey(GetProjectNamesByKeyParams params) {
    return requestAsync(cancelMonitor -> new GetProjectNamesByKeyResponse(getBean(ConnectionService.class)
      .getProjectNamesByKey(params.getTransientConnection(), params.getProjectKeys(), cancelMonitor)));
  }

  @Override
  public CompletableFuture<GetConnectionSuggestionsResponse> getConnectionSuggestions(GetConnectionSuggestionsParams params) {
    return requestAsync(
      cancelMonitor -> new GetConnectionSuggestionsResponse(getBean(ConnectionSuggestionProvider.class)
        .getConnectionSuggestions(params.getConfigurationScopeId(), cancelMonitor)));
  }

  @Override
  public CompletableFuture<GetMCPServerSettingsResponse> getMCPServerSettings(GetMCPServerSettingsParams params) {
    return requestAsync(cancelMonitor -> {
      try {
        return new GetMCPServerSettingsResponse(
          getBean(MCPServerSettingsProvider.class).getMCPServerSettingsJSON(params.getConnectionId(), params.getToken()));
      } catch (SonarLintException e) {
        var error = new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND, e.getMessage(), params.getConnectionId());
        throw new ResponseErrorException(error);
      }
    });
  }

}
