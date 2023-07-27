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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

import static org.apache.commons.lang3.StringUtils.removeEnd;

public abstract class AbstractConnectionConfiguration {

  /**
   * The id of the connection on the client side
   */
  private final String connectionId;
  private final boolean disableNotifications;
  private final ConnectionKind kind;
  private final String url;

  protected AbstractConnectionConfiguration(String connectionId, ConnectionKind kind, boolean disableNotifications, String url) {
    Objects.requireNonNull(connectionId, "Connection id is mandatory");
    this.connectionId = connectionId;
    this.kind = kind;
    this.disableNotifications = disableNotifications;
    this.url = url;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public ConnectionKind getKind() {
    return kind;
  }

  public boolean isDisableNotifications() {
    return disableNotifications;
  }

  public String getUrl() {
    return url;
  }

  public abstract EndpointParams getEndpointParams();

  public boolean isSameServerUrl(String otherUrl) {
    URI myUri;
    URI otherUri;
    try {
      myUri = new URI(removeEnd(url, "/"));
      otherUri = new URI(removeEnd(otherUrl, "/"));
    } catch (URISyntaxException e) {
      return false;
    }
    return Objects.equals(myUri, otherUri);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    var that = (AbstractConnectionConfiguration) o;
    return Objects.equals(connectionId, that.connectionId)
      && Objects.equals(disableNotifications, that.disableNotifications)
            && Objects.equals(url, that.url);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionId, url);
  }
}
