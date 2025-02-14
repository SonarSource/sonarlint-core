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

public enum SonarCloudRegion {
  EU("https://sonarcloud.io", "https://api.sonarcloud.io", "wss://events-api.sonarcloud.io/"),
  US("https://us.sonarcloud.io", "https://api.us.sonarcloud.io", "wss://events-api.us.sonarcloud.io/");

  private final URI productionUri;
  private final URI apiProductionUri;
  private final URI webSocketUri;

  SonarCloudRegion(String productionUri, String apiProductionUri, String webSocketUri) {
    this.productionUri = URI.create(productionUri);
    this.apiProductionUri = URI.create(apiProductionUri);
    this.webSocketUri = URI.create(webSocketUri);
  }

  public URI getProductionUri() {
    return productionUri;
  }

  public URI getApiProductionUri() {
    return apiProductionUri;
  }

  public URI getWebSocketUri() {
    return webSocketUri;
  }
}
