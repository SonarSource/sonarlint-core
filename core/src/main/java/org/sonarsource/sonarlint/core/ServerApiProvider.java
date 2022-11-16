/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.util.Optional;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;

import static org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration.SONARCLOUD_URL;

public class ServerApiProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarLintClient client;

  public ServerApiProvider(ConnectionConfigurationRepository connectionRepository, SonarLintClient client) {
    this.connectionRepository = connectionRepository;
    this.client = client;
  }

  public Optional<ServerApi> getServerApi(String connectionId) {
    var connectionConfig = connectionRepository.getConnectionById(connectionId);
    EndpointParams params;
    if (connectionConfig instanceof SonarQubeConnectionConfiguration) {
      params = new EndpointParams(((SonarQubeConnectionConfiguration) connectionConfig).getServerUrl(), false, null);
    } else if (connectionConfig instanceof SonarCloudConnectionConfiguration) {
      params = new EndpointParams(SONARCLOUD_URL, true, ((SonarCloudConnectionConfiguration) connectionConfig).getOrganization());
    } else {
      LOG.debug("Connection '{}' is gone", connectionId);
      return Optional.empty();
    }
    var httpClient = client.
      getHttpClient(connectionId);
    if (httpClient != null) {
      return Optional.of(new ServerApi(params, httpClient));
    } else {
      return Optional.empty();
    }
  }

}
