/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/** This is used to configure the required URIs for a SonarQube Cloud Region */
public class SonarQubeCloudRegionDto {
  @Nullable
  private final URI uri;
  @Nullable
  private final URI apiUri;
  @Nullable
  private final URI webSocketsEndpointUri;

  /**
   *  All of the URIs can be null!
   *  
   *  @param uri the base URI, e.g. https://sonarcloud.io
   *  @param apiUri the base URI of new endpoints, e.g. https://api.sonarcloud.io. Must be specified because for some env it cannot be deduced from the base URI (e.g. Dev)
   */
  public SonarQubeCloudRegionDto(@Nullable URI uri, @Nullable URI apiUri, @Nullable URI webSocketsEndpointUri) {
    this.uri = uri;
    this.apiUri = apiUri;
    this.webSocketsEndpointUri = webSocketsEndpointUri;
  }

  @CheckForNull
  public URI getUri() {
    return uri;
  }

  @CheckForNull
  public URI getApiUri() {
    return apiUri;
  }

  @CheckForNull
  public URI getWebSocketsEndpointUri() {
    return webSocketsEndpointUri;
  }
}
