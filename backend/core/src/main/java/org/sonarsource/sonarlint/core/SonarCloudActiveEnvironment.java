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
import java.util.Arrays;

import static org.apache.commons.lang.StringUtils.removeEnd;

public class SonarCloudActiveEnvironment {

  private SonarQubeCloudUris alternativeUris;

  public static SonarCloudActiveEnvironment prod() {
    return new SonarCloudActiveEnvironment();
  }

  public SonarCloudActiveEnvironment() {
  }

  public SonarCloudActiveEnvironment(URI uri, URI webSocketsEndpointUri) {
    this.alternativeUris = new SonarQubeCloudUris(uri, webSocketsEndpointUri);
  }

  public URI getUri(SonarCloudRegion region) {
    return alternativeUris != null ? alternativeUris.productionUri : region.getProductionUri();
  }

  public URI getWebSocketsEndpointUri(SonarCloudRegion region) {
    return alternativeUris != null ? alternativeUris.wsUri : region.getWebSocketUri();
  }

  public boolean isSonarQubeCloud(String uri) {
    return isAlternativeUri(uri) ||
      Arrays.stream(SonarCloudRegion.values())
      .map(u -> removeEnd(u.getProductionUri().toString(), "/"))
      .anyMatch(u -> u.equals(removeEnd(uri, "/")));
  }

  /**
   *  Before calling this method, caller should make sure URI is SonarCloud
   */
  public SonarCloudRegion getRegionOrThrow(String uri) {
    if (isAlternativeUri(uri)) {
      return SonarCloudRegion.EU;
    }
    return Arrays.stream(SonarCloudRegion.values())
      .filter(e -> removeEnd(e.getProductionUri().toString(), "/").equals(removeEnd(uri, "/")))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("URI should be a known SonarCloud URI"));
  }

  private boolean isAlternativeUri(String uri) {
    return alternativeUris != null && removeEnd(alternativeUris.productionUri.toString(), "/").equals(removeEnd(uri, "/"));
  }

  private static class SonarQubeCloudUris {
    private final URI productionUri;
    private final URI wsUri;

    public SonarQubeCloudUris(URI productionUri, URI wsUri) {
      this.productionUri = productionUri;
      this.wsUri = wsUri;
    }

    public URI getProductionUri() {
      return productionUri;
    }

    public URI getWsUri() {
      return wsUri;
    }
  }
}
