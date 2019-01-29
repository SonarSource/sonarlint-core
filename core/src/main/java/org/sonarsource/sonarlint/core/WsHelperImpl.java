/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Organizations;
import org.sonarqube.ws.WsComponents;
import org.sonarqube.ws.WsComponents.ShowWsResponse;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteProject;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.AuthenticationChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.DefaultValidationResult;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.model.DefaultRemoteOrganization;
import org.sonarsource.sonarlint.core.container.model.DefaultRemoteProject;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import static java.util.Objects.requireNonNull;

public class WsHelperImpl implements WsHelper {
  private static final Logger LOG = Loggers.get(WsHelperImpl.class);

  private static final String MIN_VERSION_FOR_ORGANIZATIONS = "6.3";

  @Override
  public ValidationResult validateConnection(ServerConfiguration serverConfig) {
    return validateConnection(createClient(serverConfig), serverConfig.getOrganizationKey());
  }

  static ValidationResult validateConnection(SonarLintWsClient client, @Nullable String organizationKey) {
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(client);
    AuthenticationChecker authChecker = new AuthenticationChecker(client);
    try {
      ServerInfos serverStatus = serverChecker.checkVersionAndStatus();
      ValidationResult validateCredentials = authChecker.validateCredentials();
      if (validateCredentials.success() && organizationKey != null) {
        Version serverVersion = Version.create(serverStatus.getVersion());
        if (serverVersion.compareToIgnoreQualifier(Version.create(MIN_VERSION_FOR_ORGANIZATIONS)) < 0) {
          return new DefaultValidationResult(false, "No organization support for this server version: " + serverStatus.getVersion());
        }
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

  private static SonarLintWsClient createClient(ServerConfiguration serverConfig) {
    requireNonNull(serverConfig);
    return new SonarLintWsClient(serverConfig);
  }

  static String generateAuthenticationToken(ServerVersionAndStatusChecker serverChecker, SonarLintWsClient client, String name, boolean force) {
    try {
      // in 5.3 login is mandatory and user needs admin privileges
      serverChecker.checkVersionAndStatus("5.4");

      if (force) {
        // revoke
        client.post("api/user_tokens/revoke?name=" + name);
      }

      // create
      try (WsResponse response = client.post("api/user_tokens/generate?name=" + name)) {
        Map<?, ?> javaRootMapObject = new Gson().fromJson(response.content(), Map.class);
        return (String) javaRootMapObject.get("token");
      }
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  @Override
  public String generateAuthenticationToken(ServerConfiguration serverConfig, String name, boolean force) {
    SonarLintWsClient client = createClient(serverConfig);
    return generateAuthenticationToken(new ServerVersionAndStatusChecker(client), client, name, force);
  }

  @Override
  public List<RemoteOrganization> listOrganizations(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor) {
    SonarLintWsClient client = createClient(serverConfig);
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(client);
    return listOrganizations(client, serverChecker, false, new ProgressWrapper(monitor));
  }

  @Override
  public List<RemoteOrganization> listUserOrganizations(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor) {
    SonarLintWsClient client = createClient(serverConfig);
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(client);
    return listOrganizations(client, serverChecker, true, new ProgressWrapper(monitor));
  }

  @Override
  public Optional<RemoteOrganization> getOrganization(ServerConfiguration serverConfig, String organizationKey, @Nullable ProgressMonitor monitor) {
    SonarLintWsClient client = createClient(serverConfig);
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(client);
    return getOrganization(client, serverChecker, organizationKey, new ProgressWrapper(monitor));
  }

  @Override
  public Optional<RemoteProject> getProject(ServerConfiguration serverConfig, String projectKey, ProgressMonitor monitor) {
    SonarLintWsClient client = createClient(serverConfig);
    return fetchComponent(client, projectKey)
      .map(DefaultRemoteProject::new);
  }

  public static Optional<ShowWsResponse> fetchComponent(SonarLintWsClient client, String componentKey) {
    return SonarLintWsClient.processTimed(
      () -> client.rawGet("api/components/show.protobuf?component=" + StringUtils.urlEncode(componentKey)),
      response -> {
        if (response.isSuccessful()) {
          return Optional.of(WsComponents.ShowWsResponse.parseFrom(response.contentStream()));
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

  static List<RemoteOrganization> listOrganizations(SonarLintWsClient client, ServerVersionAndStatusChecker serverChecker, boolean memberOnly, ProgressWrapper progress) {
    try {
      checkServer(serverChecker, progress);
      return fetchOrganizations(client, memberOnly, progress.subProgress(0.2f, 1.0f, "Fetch organizations"));
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private static void checkServer(ServerVersionAndStatusChecker serverChecker, ProgressWrapper progress) {
    progress.setProgressAndCheckCancel("Check server version", 0.1f);
    serverChecker.checkVersionAndStatus(MIN_VERSION_FOR_ORGANIZATIONS);
    progress.setProgressAndCheckCancel("Fetch organizations", 0.2f);
  }

  private static Optional<RemoteOrganization> fetchOrganization(SonarLintWsClient client, String organizationKey, ProgressWrapper progress) {
    String url = "api/organizations/search.protobuf?organizations=" + StringUtils.urlEncode(organizationKey);
    return getPaginatedOrganizations(client, url, progress)
      .stream()
      .findFirst();
  }

  private static List<RemoteOrganization> fetchOrganizations(SonarLintWsClient client, boolean memberOnly, ProgressWrapper progress) {
    String url = "api/organizations/search.protobuf";
    if (memberOnly) {
      url += "?member=true";
    }

    return getPaginatedOrganizations(client, url, progress);
  }

  private static List<RemoteOrganization> getPaginatedOrganizations(SonarLintWsClient client, String url, ProgressWrapper progress) {
    List<RemoteOrganization> result = new ArrayList<>();

    SonarLintWsClient.getPaginated(client, url,
      Organizations.SearchWsResponse::parseFrom,
      Organizations.SearchWsResponse::getPaging,
      Organizations.SearchWsResponse::getOrganizationsList,
      org -> result.add(new DefaultRemoteOrganization(org)),
      false,
      progress);

    return result;
  }
}
