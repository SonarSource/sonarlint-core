/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.repository.connection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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

  public boolean hasConnectionWithOrigin(String serverOrigin) {
    // The Origin header has the following format: <scheme>://<host>(:<port>)
    // Since servers can have an optional "context path" after this, we consider a valid match when the server's configured URL begins with the
    // passed Origin
    // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Origin
    return connectionsById.values().stream()
      .anyMatch(connection -> getUrl(connection).startsWith(serverOrigin));
  }

  public List<AbstractConnectionConfiguration> findByUrl(String serverUrl) {
    return connectionsById.values().stream()
      .filter(connection -> equalsIgnoringTrailingSlash(getUrl(connection), serverUrl))
      .collect(Collectors.toList());
  }

  private static String getUrl(AbstractConnectionConfiguration connectionConfig) {
    if (connectionConfig instanceof SonarQubeConnectionConfiguration) {
      return ((SonarQubeConnectionConfiguration) connectionConfig).getServerUrl();
    }
    return SONARCLOUD_URL;
  }

  private static boolean equalsIgnoringTrailingSlash(String aString, String anotherString) {
    return withTrailingSlash(aString).equals(withTrailingSlash(anotherString));
  }

  private static String withTrailingSlash(String str) {
    if (!str.endsWith("/")) {
      return str + '/';
    }
    return str;
  }
}
