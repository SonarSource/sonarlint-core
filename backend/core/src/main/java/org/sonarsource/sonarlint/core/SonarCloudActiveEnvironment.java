/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.net.URI;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarQubeCloudRegionDto;

import static org.apache.commons.lang.StringUtils.removeEnd;

public class SonarCloudActiveEnvironment {
  private SonarQubeCloudRegionDto alternativeEuRegion;
  private SonarQubeCloudRegionDto alternativeUsRegion;

  public static SonarCloudActiveEnvironment prod() {
    return new SonarCloudActiveEnvironment(new SonarQubeCloudRegionDto(null, null, null),
      new SonarQubeCloudRegionDto(null, null, null));
  }
  
  public SonarCloudActiveEnvironment(SonarQubeCloudRegionDto alternativeEuRegion, SonarQubeCloudRegionDto alternativeUsRegion) {
    this.alternativeEuRegion = alternativeEuRegion;
    this.alternativeUsRegion = alternativeUsRegion;
  }

  public URI getUri(SonarCloudRegion region) {
    if (region.equals(SonarCloudRegion.US)) {
      return alternativeUsRegion.getUri() != null
        ? alternativeUsRegion.getUri()
        : SonarCloudRegion.US.getProductionUri();
    }
    return alternativeEuRegion.getUri() != null
      ? alternativeEuRegion.getUri()
      : SonarCloudRegion.EU.getProductionUri();
  }

  public URI getApiUri(SonarCloudRegion region) {
    if (region.equals(SonarCloudRegion.US)) {
      return alternativeUsRegion.getApiUri() != null
        ? alternativeUsRegion.getApiUri()
        : SonarCloudRegion.US.getApiProductionUri();
    }
    return alternativeEuRegion.getApiUri() != null
      ? alternativeEuRegion.getApiUri()
      : SonarCloudRegion.EU.getApiProductionUri();
  }

  public URI getWebSocketsEndpointUri(SonarCloudRegion region) {
    if (region.equals(SonarCloudRegion.US)) {
      return alternativeUsRegion.getWebSocketsEndpointUri() != null
        ? alternativeUsRegion.getWebSocketsEndpointUri()
        : SonarCloudRegion.US.getWebSocketUri();
    }
    return alternativeEuRegion.getWebSocketsEndpointUri() != null
      ? alternativeEuRegion.getWebSocketsEndpointUri()
      : SonarCloudRegion.EU.getWebSocketUri();
  }

  public boolean isSonarQubeCloud(String uri) {
    var cleanedUri = removeEnd(uri, "/");
    if (isAlternativeUsUri(cleanedUri) || isAlternativeEuUri(cleanedUri)) {
      return true;
    }
    
    return removeEnd(SonarCloudRegion.US.getProductionUri().toString(), "/").equals(cleanedUri) ||
      removeEnd(SonarCloudRegion.EU.getProductionUri().toString(), "/").equals(cleanedUri);
  }

  /**
   *  Before calling this method, caller should make sure URI is SonarCloud
   */
  public SonarCloudRegion getRegionOrThrow(String uri) {
    var cleanedUri = removeEnd(uri, "/");
    if (isAlternativeUsUri(cleanedUri) ||
      removeEnd(SonarCloudRegion.US.getProductionUri().toString(), "/").equals(cleanedUri)) {
      return SonarCloudRegion.US;
    } else if (isAlternativeEuUri(cleanedUri) ||
      removeEnd(SonarCloudRegion.EU.getProductionUri().toString(), "/").equals(cleanedUri)) {
      return SonarCloudRegion.EU;
    }
    
    throw new IllegalArgumentException("URI should be a known SonarCloud URI");
  }
  
  private boolean isAlternativeEuUri(String uri) {
    return alternativeEuRegion.getUri() != null
      && removeEnd(alternativeEuRegion.getUri().toString(), "/").equals(uri);
  }

  private boolean isAlternativeUsUri(String uri) {
    return alternativeUsRegion.getUri() != null
      && removeEnd(alternativeUsRegion.getUri().toString(), "/").equals(uri);
  }
}
