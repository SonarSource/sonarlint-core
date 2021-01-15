/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Components;
import org.sonarqube.ws.Components.ShowWsResponse;
import org.sonarqube.ws.Organizations;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteProject;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.hotspot.SecurityHotspotsService;
import org.sonarsource.sonarlint.core.container.connected.validate.AuthenticationChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.DefaultValidationResult;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.model.DefaultRemoteOrganization;
import org.sonarsource.sonarlint.core.container.model.DefaultRemoteProject;
import org.sonarsource.sonarlint.core.http.ConnectedModeEndpoint;
import org.sonarsource.sonarlint.core.http.SonarLintHttpClient;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static java.util.Objects.requireNonNull;

public class WsHelperImpl implements WsHelper {
  private static final Logger LOG = Loggers.get(WsHelperImpl.class);
  private final SonarLintHttpClient client;

  public WsHelperImpl(SonarLintHttpClient client) {
    this.client = client;
  }

  @Override
  public ValidationResult validateConnection(ConnectedModeEndpoint endpoint) {
    return validateConnection(createClient(endpoint), endpoint.isSonarCloud() ? endpoint.getOrganization() : null);
  }

  private static ValidationResult validateConnection(SonarLintWsClient client, @Nullable String organizationKey) {
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(client);
    AuthenticationChecker authChecker = new AuthenticationChecker(client);
    try {
      serverChecker.checkVersionAndStatus();
      ValidationResult validateCredentials = authChecker.validateCredentials();
      if (validateCredentials.success() && organizationKey != null) {
        if (!fetchOrganization(client, organizationKey, new ProgressWrapper(null)).isPresent()) {
          return new DefaultValidationResult(false, "No organizations found for key: " + organizationKey);
        }
      }
      return validateCredentials;
    } catch (UnsupportedServerException e) {
      return new DefaultValidationResult(false, e.getMessage());
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private SonarLintWsClient createClient(ConnectedModeEndpoint endpoint) {
    requireNonNull(endpoint);
    return new SonarLintWsClient(endpoint, client);
  }

  @Override
  public List<RemoteOrganization> listUserOrganizations(ConnectedModeEndpoint endpoint, @Nullable ProgressMonitor monitor) {
    SonarLintWsClient client = createClient(endpoint);
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(client);
    return listUserOrganizations(client, serverChecker, new ProgressWrapper(monitor));
  }

  @Override
  public Optional<RemoteOrganization> getOrganization(ConnectedModeEndpoint endpoint, String organizationKey, @Nullable ProgressMonitor monitor) {
    SonarLintWsClient wsClient = createClient(endpoint);
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(wsClient);
    return getOrganization(wsClient, serverChecker, organizationKey, new ProgressWrapper(monitor));
  }

  @Override
  public Optional<RemoteProject> getProject(ConnectedModeEndpoint endpoint, String projectKey, ProgressMonitor monitor) {
    SonarLintWsClient wsClient = createClient(endpoint);
    return fetchComponent(wsClient, projectKey)
      .map(DefaultRemoteProject::new);
  }

  @Override
  public Optional<RemoteHotspot> getHotspot(ConnectedModeEndpoint endpoint, GetSecurityHotspotRequestParams requestParams) {
    SecurityHotspotsService service = new SecurityHotspotsService(endpoint, client);
    return service.fetch(requestParams);
  }

  public static Optional<ShowWsResponse> fetchComponent(SonarLintWsClient client, String componentKey) {
    return SonarLintWsClient.processTimed(
      () -> client.rawGet("api/components/show.protobuf?component=" + StringUtils.urlEncode(componentKey)),
      response -> {
        if (response.isSuccessful()) {
          return Optional.of(Components.ShowWsResponse.parseFrom(response.bodyAsStream()));
        }
        return Optional.empty();
      },
      duration -> LOG.debug("Downloaded project details in {}ms", duration));
  }

  static Optional<RemoteOrganization> getOrganization(SonarLintWsClient client, ServerVersionAndStatusChecker serverChecker, String organizationKey, ProgressWrapper progress) {
    try {
      checkServer(serverChecker, progress);
      return fetchOrganization(client, organizationKey, progress.subProgress(0.2f, 1.0f, "Fetch organization"));
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  static List<RemoteOrganization> listUserOrganizations(SonarLintWsClient client, ServerVersionAndStatusChecker serverChecker, ProgressWrapper progress) {
    try {
      checkServer(serverChecker, progress);
      return fetchUserOrganizations(client, progress.subProgress(0.2f, 1.0f, "Fetch organizations"));
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private static void checkServer(ServerVersionAndStatusChecker serverChecker, ProgressWrapper progress) {
    progress.setProgressAndCheckCancel("Check server version", 0.1f);
    serverChecker.checkVersionAndStatus();
    progress.setProgressAndCheckCancel("Fetch organizations", 0.2f);
  }

  private static Optional<RemoteOrganization> fetchOrganization(SonarLintWsClient client, String organizationKey, ProgressWrapper progress) {
    String url = "api/organizations/search.protobuf?organizations=" + StringUtils.urlEncode(organizationKey);
    return getPaginatedOrganizations(client, url, progress)
      .stream()
      .findFirst();
  }

  private static List<RemoteOrganization> fetchUserOrganizations(SonarLintWsClient client, ProgressWrapper progress) {
    String url = "api/organizations/search.protobuf?member=true";
    return getPaginatedOrganizations(client, url, progress);
  }

  private static List<RemoteOrganization> getPaginatedOrganizations(SonarLintWsClient client, String url, ProgressWrapper progress) {
    List<RemoteOrganization> result = new ArrayList<>();

    client.getPaginated(url,
      Organizations.SearchWsResponse::parseFrom,
      Organizations.SearchWsResponse::getPaging,
      Organizations.SearchWsResponse::getOrganizationsList,
      org -> result.add(new DefaultRemoteOrganization(org)),
      false,
      progress);

    return result;
  }
}
