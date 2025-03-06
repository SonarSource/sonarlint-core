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

/** This contains the alternative environment information for SonarQube Cloud with both the EU and US region */
public class SonarCloudAlternativeEnvironmentDto {
  private final SonarQubeCloudRegionDto euRegion;
  private final SonarQubeCloudRegionDto usRegion;

  /**
   * @deprecated use the other constructor instead
   */
  @Deprecated(since = "10.16")
  public SonarCloudAlternativeEnvironmentDto(URI uri, URI webSocketsEndpointUri) {
    this(uri, uri, webSocketsEndpointUri);
  }

  /**
   *
   * @deprecated use the other constructor instead
   * @param uri the base URI, e.g. https://sonarcloud.io
   * @param apiUri the base URI of new endpoints, e.g. https://api.sonarcloud.io. Must be specified because for some env it cannot be deduced from the base URI (e.g. Dev)
   */
  @Deprecated(since = "10.17")
  public SonarCloudAlternativeEnvironmentDto(URI uri, URI apiUri, URI webSocketsEndpointUri) {
    this(new SonarQubeCloudRegionDto(uri, apiUri, webSocketsEndpointUri), new SonarQubeCloudRegionDto(null, null, null));
  }
  
  public SonarCloudAlternativeEnvironmentDto(SonarQubeCloudRegionDto euRegion, SonarQubeCloudRegionDto usRegion) {
    this.euRegion = euRegion;
    this.usRegion = usRegion;
  }
  
  public SonarQubeCloudRegionDto getEuRegion() {
    return euRegion;
  }

  public SonarQubeCloudRegionDto getUsRegion() {
    return usRegion;
  }
}
