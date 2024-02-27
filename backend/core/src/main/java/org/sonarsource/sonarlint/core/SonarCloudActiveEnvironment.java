/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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

  public static final URI PRODUCTION_URI = URI.create("https://sonarcloud.io");

  public static SonarCloudActiveEnvironment prod() {
    return new SonarCloudActiveEnvironment(PRODUCTION_URI, URI.create("wss://events-api.sonarcloud.io/"));
  }

  private final URI uri;
  private final URI webSocketsEndpointUri;

  public SonarCloudActiveEnvironment(URI uri, URI webSocketsEndpointUri) {
    this.uri = uri;
    this.webSocketsEndpointUri = webSocketsEndpointUri;
  }

  public URI getUri() {
    return uri;
  }

  public URI getWebSocketsEndpointUri() {
    return webSocketsEndpointUri;
  }
}
