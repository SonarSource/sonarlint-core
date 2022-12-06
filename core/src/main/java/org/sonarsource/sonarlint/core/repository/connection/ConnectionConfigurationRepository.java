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
package org.sonarsource.sonarlint.core.repository.connection;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

import static org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration.SONARCLOUD_URL;

public class ConnectionConfigurationRepository {

  private final Map<String, AbstractConnectionConfiguration> connectionsById = new ConcurrentHashMap<>();

  /**
   * Add or replace connection configuration.
   * @return the previous configuration with the same id, if any
   */
  @CheckForNull
  public AbstractConnectionConfiguration addOrReplace(AbstractConnectionConfiguration connectionConfiguration) {
    return connectionsById.put(connectionConfiguration.getConnectionId(), connectionConfiguration);
  }

  /**
   * Remove a connection configuration.
   * @return the removed configuration, if any
   */
  @CheckForNull
  public AbstractConnectionConfiguration remove(String idToRemove) {
    return connectionsById.remove(idToRemove);
  }

  public Map<String, AbstractConnectionConfiguration> getConnectionsById() {
    return Map.copyOf(connectionsById);
  }

  @CheckForNull
  public AbstractConnectionConfiguration getConnectionById(String id) {
    return connectionsById.get(id);
  }

  public Optional<EndpointParams> getEndpointParams(String connectionId) {
    var connectionConfig = getConnectionById(connectionId);
    if (connectionConfig instanceof SonarQubeConnectionConfiguration) {
      return Optional.of(new EndpointParams(((SonarQubeConnectionConfiguration) connectionConfig).getServerUrl(), false, null));
    } else if (connectionConfig instanceof SonarCloudConnectionConfiguration) {
      return Optional.of(new EndpointParams(SONARCLOUD_URL, true, ((SonarCloudConnectionConfiguration) connectionConfig).getOrganization()));
    } else {
      return Optional.empty();
    }
  }
}
