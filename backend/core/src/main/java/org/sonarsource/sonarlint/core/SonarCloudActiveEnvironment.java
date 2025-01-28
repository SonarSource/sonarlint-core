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
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang.StringUtils.removeEnd;

public class SonarCloudActiveEnvironment {

  public static final URI PRODUCTION_EU_URI = URI.create("https://sonarcloud.io");
  public static final URI PRODUCTION_US_URI = URI.create("https://us.sonarcloud.io");
  public static final URI WS_EU_ENDPOINT_URI = URI.create("wss://events-api.sonarcloud.io/");
  // TODO provide the correct URL
  public static final URI WS_US_ENDPOINT_URI = URI.create("wss://events-api.sonarcloud.io/");

  public static SonarCloudActiveEnvironment prod() {
    return new SonarCloudActiveEnvironment();
  }

  private final Map<SonarCloudRegion, SonarQubeCloudUris> uris;

  public SonarCloudActiveEnvironment() {
    var euUris = new SonarQubeCloudUris(PRODUCTION_EU_URI, WS_EU_ENDPOINT_URI);
    var usUris = new SonarQubeCloudUris(PRODUCTION_US_URI, WS_US_ENDPOINT_URI);
    this.uris = Map.of(SonarCloudRegion.EU, euUris, SonarCloudRegion.US, usUris);
  }

  public SonarCloudActiveEnvironment(URI uri, URI webSocketsEndpointUri) {
    this.uris = Map.of(SonarCloudRegion.EU, new SonarQubeCloudUris(uri, webSocketsEndpointUri));
  }

  public URI getUri(SonarCloudRegion region) {
    return uris.get(region).getProductionUri();
  }

  public URI getWebSocketsEndpointUri(SonarCloudRegion region) {
    return uris.get(region).getWsUri();
  }

  public boolean isSonarQubeCloud(String uri) {
    return uris.values()
      .stream()
      .map(u -> removeEnd(u.getProductionUri().toString(), "/"))
      .anyMatch(u -> u.equals(removeEnd(uri, "/")));
  }

  public Optional<SonarCloudRegion> getRegion(String uri) {
    return uris.entrySet()
      .stream()
      .filter(e -> removeEnd(e.getValue().getProductionUri().toString(), "/").equals(removeEnd(uri, "/")))
      .findFirst()
      .map(Map.Entry::getKey);
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
