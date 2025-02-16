/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import java.net.URI;

public class SonarCloudAlternativeEnvironmentDto {
  private final URI uri;
  private final URI apiUri;
  private final URI webSocketsEndpointUri;

  /**
   * @deprecated use the other constructor instead
   */
  @Deprecated(since = "10.16")
  public SonarCloudAlternativeEnvironmentDto(URI uri, URI webSocketsEndpointUri) {
    this(uri, uri, webSocketsEndpointUri);
  }

  /**
   *
   * @param uri the base URI, e.g. https://sonarcloud.io
   * @param apiUri the base URI of new endpoints, e.g. https://api.sonarcloud.io. Must be specified because for some env it cannot be deduced from the base URI (e.g. Dev)
   */
  public SonarCloudAlternativeEnvironmentDto(URI uri, URI apiUri, URI webSocketsEndpointUri) {
    this.uri = uri;
    this.apiUri = apiUri;
    this.webSocketsEndpointUri = webSocketsEndpointUri;
  }

  public URI getUri() {
    return uri;
  }

  public URI getApiUri() {
    return apiUri;
  }

  public URI getWebSocketsEndpointUri() {
    return webSocketsEndpointUri;
  }
}
