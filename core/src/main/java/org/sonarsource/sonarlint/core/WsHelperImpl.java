/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
import javax.annotation.Nullable;
import org.sonarqube.ws.Organizations;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
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
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import static com.google.common.base.Preconditions.checkNotNull;

public class WsHelperImpl implements WsHelper {
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
        if (fetchOrganizations(client, organizationKey, new ProgressWrapper(null)).isEmpty()) {
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
    checkNotNull(serverConfig);
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
    return listOrganizations(client, serverChecker, new ProgressWrapper(monitor));
  }

  static List<RemoteOrganization> listOrganizations(SonarLintWsClient client, ServerVersionAndStatusChecker serverChecker, ProgressWrapper progress) {
    try {
      progress.setProgressAndCheckCancel("Check server version", 0.1f);
      serverChecker.checkVersionAndStatus(MIN_VERSION_FOR_ORGANIZATIONS);
      progress.setProgressAndCheckCancel("Fetch organizations", 0.2f);
      return fetchOrganizations(client, null, progress.subProgress(0.2f, 1.0f));
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private static List<RemoteOrganization> fetchOrganizations(SonarLintWsClient client, @Nullable String organizationKey, ProgressWrapper progress) {
    List<RemoteOrganization> result = new ArrayList<>();

    String url = "api/organizations/search.protobuf";
    if (organizationKey != null) {
      url += "?organizations=" + StringUtils.urlEncode(organizationKey);
    }

    SonarLintWsClient.getPaginated(client, url,
      Organizations.SearchWsResponse::parseFrom,
      Organizations.SearchWsResponse::getPaging,
      Organizations.SearchWsResponse::getOrganizationsList,

      org -> result.add(new DefaultRemoteOrganization(org)),
      progress);

    return result;
  }
}
