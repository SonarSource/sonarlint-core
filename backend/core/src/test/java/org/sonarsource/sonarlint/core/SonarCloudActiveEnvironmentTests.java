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
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarQubeCloudRegionDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SonarCloudActiveEnvironmentTests {
  private static URI baseUri = URI.create("baseUri");
  private static URI apiUri = URI.create("apiUri");
  private static URI webSocketUri = URI.create("webSocketUri");
  
  private static SonarQubeCloudRegionDto regionWithBaseUri = new SonarQubeCloudRegionDto(baseUri, null, null);
  private static SonarQubeCloudRegionDto regionWithApiUri = new SonarQubeCloudRegionDto(null, apiUri, null);
  private static SonarQubeCloudRegionDto regionWithWebSocketUri = new SonarQubeCloudRegionDto(null, null, webSocketUri);
  
  @Test
  void test_getUri() {
    assertThat(new SonarCloudActiveEnvironment(null, null).getUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getProductionUri());
    assertThat(new SonarCloudActiveEnvironment(null, null).getUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getProductionUri());

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, null).getUri(SonarCloudRegion.EU))
      .isEqualTo(baseUri);
    assertThat(new SonarCloudActiveEnvironment(null, regionWithBaseUri).getUri(SonarCloudRegion.US))
      .isEqualTo(baseUri);

    assertThat(new SonarCloudActiveEnvironment(regionWithApiUri, null).getUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getProductionUri());
    assertThat(new SonarCloudActiveEnvironment(null, regionWithApiUri).getUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getProductionUri());
  }

  @Test
  void test_getApiUri() {
    assertThat(new SonarCloudActiveEnvironment(null, null).getApiUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getApiProductionUri());
    assertThat(new SonarCloudActiveEnvironment(null, null).getApiUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getApiProductionUri());

    assertThat(new SonarCloudActiveEnvironment(regionWithApiUri, null).getApiUri(SonarCloudRegion.EU))
      .isEqualTo(apiUri);
    assertThat(new SonarCloudActiveEnvironment(null, regionWithApiUri).getApiUri(SonarCloudRegion.US))
      .isEqualTo(apiUri);

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, null).getApiUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getApiProductionUri());
    assertThat(new SonarCloudActiveEnvironment(null, regionWithBaseUri).getApiUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getApiProductionUri());
  }

  @Test
  void test_getWebSocketsEndpointUri() {
    assertThat(new SonarCloudActiveEnvironment(null, null).getWebSocketsEndpointUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getWebSocketUri());
    assertThat(new SonarCloudActiveEnvironment(null, null).getWebSocketsEndpointUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getWebSocketUri());

    assertThat(new SonarCloudActiveEnvironment(regionWithWebSocketUri, null).getWebSocketsEndpointUri(SonarCloudRegion.EU))
      .isEqualTo(webSocketUri);
    assertThat(new SonarCloudActiveEnvironment(null, regionWithWebSocketUri).getWebSocketsEndpointUri(SonarCloudRegion.US))
      .isEqualTo(webSocketUri);

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, null).getWebSocketsEndpointUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getWebSocketUri());
    assertThat(new SonarCloudActiveEnvironment(null, regionWithBaseUri).getWebSocketsEndpointUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getWebSocketUri());
  }
  
  @Test
  void test_isSonarQubeCloud() {
    assertThat(new SonarCloudActiveEnvironment(null, null).isSonarQubeCloud("aaaa")).isFalse();

    assertThat(new SonarCloudActiveEnvironment(null, null)
      .isSonarQubeCloud(SonarCloudRegion.EU.getProductionUri().toString())).isTrue();
    assertThat(new SonarCloudActiveEnvironment(null, null)
      .isSonarQubeCloud(SonarCloudRegion.US.getProductionUri().toString())).isTrue();

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, null)
      .isSonarQubeCloud(baseUri.toString())).isTrue();
    assertThat(new SonarCloudActiveEnvironment(regionWithApiUri, null)
      .isSonarQubeCloud(SonarCloudRegion.EU.getProductionUri().toString())).isTrue();
    assertThat(new SonarCloudActiveEnvironment(null, regionWithBaseUri)
      .isSonarQubeCloud(baseUri.toString())).isTrue();
    assertThat(new SonarCloudActiveEnvironment(null, regionWithApiUri)
      .isSonarQubeCloud(SonarCloudRegion.US.getProductionUri().toString())).isTrue();
  }

  @Test
  void test_getRegionOrThrow() {
    assertThatThrownBy(() -> new SonarCloudActiveEnvironment(null, null).getRegionOrThrow("aaaa"))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(new SonarCloudActiveEnvironment(null, null)
      .getRegionOrThrow(SonarCloudRegion.EU.getProductionUri().toString())).isEqualTo(SonarCloudRegion.EU);
    assertThat(new SonarCloudActiveEnvironment(null, null)
      .getRegionOrThrow(SonarCloudRegion.US.getProductionUri().toString())).isEqualTo(SonarCloudRegion.US);

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, null)
      .getRegionOrThrow(baseUri.toString())).isEqualTo(SonarCloudRegion.EU);
    assertThat(new SonarCloudActiveEnvironment(null, regionWithBaseUri)
      .getRegionOrThrow(baseUri.toString())).isEqualTo(SonarCloudRegion.US);
  }
}
