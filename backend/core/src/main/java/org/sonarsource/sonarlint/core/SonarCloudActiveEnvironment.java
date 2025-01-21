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

public class SonarCloudActiveEnvironment {

  public static final URI PRODUCTION_EU_URI = URI.create("https://sonarcloud.io");
  public static final URI PRODUCTION_US_URI = URI.create("https://us.sonarcloud.io");
  public static final URI WS_EU_ENDPOINT_URI = URI.create("wss://events-api.sonarcloud.io/");
  public static final URI WS_US_ENDPOINT_URI = URI.create("wss://events-api.sonarcloud.io/");

  public static SonarCloudActiveEnvironment prodEu() {
    return new SonarCloudActiveEnvironment(PRODUCTION_EU_URI, WS_EU_ENDPOINT_URI, SonarCloudRegion.EU);
  }

  public static SonarCloudActiveEnvironment prodUs() {
    return new SonarCloudActiveEnvironment(PRODUCTION_US_URI, WS_US_ENDPOINT_URI, SonarCloudRegion.US);
  }

  private final SonarCloudRegion region;
  private final URI uri;
  private final URI webSocketsEndpointUri;

  public SonarCloudActiveEnvironment(URI uri, URI webSocketsEndpointUri, SonarCloudRegion region) {
    this.uri = uri;
    this.region = region;
    this.webSocketsEndpointUri = webSocketsEndpointUri;
  }

  public URI getUri() {
    return uri;
  }

  public URI getWebSocketsEndpointUri() {
    return webSocketsEndpointUri;
  }

  public SonarCloudRegion getRegion() {
    return region;
  }
}
