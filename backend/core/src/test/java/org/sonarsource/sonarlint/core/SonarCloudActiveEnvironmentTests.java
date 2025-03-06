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
  private static SonarQubeCloudRegionDto empty = new SonarQubeCloudRegionDto(null, null, null);
  
  @Test
  void test_getUri() {
    assertThat(new SonarCloudActiveEnvironment(empty, empty).getUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getProductionUri());
    assertThat(new SonarCloudActiveEnvironment(empty, empty).getUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getProductionUri());

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, empty).getUri(SonarCloudRegion.EU))
      .isEqualTo(baseUri);
    assertThat(new SonarCloudActiveEnvironment(empty, regionWithBaseUri).getUri(SonarCloudRegion.US))
      .isEqualTo(baseUri);

    assertThat(new SonarCloudActiveEnvironment(regionWithApiUri, empty).getUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getProductionUri());
    assertThat(new SonarCloudActiveEnvironment(empty, regionWithApiUri).getUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getProductionUri());
  }

  @Test
  void test_getApiUri() {
    assertThat(new SonarCloudActiveEnvironment(empty, empty).getApiUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getApiProductionUri());
    assertThat(new SonarCloudActiveEnvironment(empty, empty).getApiUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getApiProductionUri());

    assertThat(new SonarCloudActiveEnvironment(regionWithApiUri, empty).getApiUri(SonarCloudRegion.EU))
      .isEqualTo(apiUri);
    assertThat(new SonarCloudActiveEnvironment(empty, regionWithApiUri).getApiUri(SonarCloudRegion.US))
      .isEqualTo(apiUri);

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, empty).getApiUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getApiProductionUri());
    assertThat(new SonarCloudActiveEnvironment(empty, regionWithBaseUri).getApiUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getApiProductionUri());
  }

  @Test
  void test_getWebSocketsEndpointUri() {
    assertThat(new SonarCloudActiveEnvironment(empty, empty).getWebSocketsEndpointUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getWebSocketUri());
    assertThat(new SonarCloudActiveEnvironment(empty, empty).getWebSocketsEndpointUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getWebSocketUri());

    assertThat(new SonarCloudActiveEnvironment(regionWithWebSocketUri, empty).getWebSocketsEndpointUri(SonarCloudRegion.EU))
      .isEqualTo(webSocketUri);
    assertThat(new SonarCloudActiveEnvironment(empty, regionWithWebSocketUri).getWebSocketsEndpointUri(SonarCloudRegion.US))
      .isEqualTo(webSocketUri);

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, empty).getWebSocketsEndpointUri(SonarCloudRegion.EU))
      .isEqualTo(SonarCloudRegion.EU.getWebSocketUri());
    assertThat(new SonarCloudActiveEnvironment(empty, regionWithBaseUri).getWebSocketsEndpointUri(SonarCloudRegion.US))
      .isEqualTo(SonarCloudRegion.US.getWebSocketUri());
  }
  
  @Test
  void test_isSonarQubeCloud() {
    assertThat(new SonarCloudActiveEnvironment(empty, empty).isSonarQubeCloud("aaaa")).isFalse();

    assertThat(new SonarCloudActiveEnvironment(empty, empty)
      .isSonarQubeCloud(SonarCloudRegion.EU.getProductionUri().toString())).isTrue();
    assertThat(new SonarCloudActiveEnvironment(empty, empty)
      .isSonarQubeCloud(SonarCloudRegion.US.getProductionUri().toString())).isTrue();

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, empty)
      .isSonarQubeCloud(baseUri.toString())).isTrue();
    assertThat(new SonarCloudActiveEnvironment(regionWithApiUri, empty)
      .isSonarQubeCloud(SonarCloudRegion.EU.getProductionUri().toString())).isTrue();
    assertThat(new SonarCloudActiveEnvironment(empty, regionWithBaseUri)
      .isSonarQubeCloud(baseUri.toString())).isTrue();
    assertThat(new SonarCloudActiveEnvironment(empty, regionWithApiUri)
      .isSonarQubeCloud(SonarCloudRegion.US.getProductionUri().toString())).isTrue();
  }

  @Test
  void test_getRegionOrThrow() {
    assertThatThrownBy(() -> new SonarCloudActiveEnvironment(empty, empty).getRegionOrThrow("aaaa"))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(new SonarCloudActiveEnvironment(empty, empty)
      .getRegionOrThrow(SonarCloudRegion.EU.getProductionUri().toString())).isEqualTo(SonarCloudRegion.EU);
    assertThat(new SonarCloudActiveEnvironment(empty, empty)
      .getRegionOrThrow(SonarCloudRegion.US.getProductionUri().toString())).isEqualTo(SonarCloudRegion.US);

    assertThat(new SonarCloudActiveEnvironment(regionWithBaseUri, empty)
      .getRegionOrThrow(baseUri.toString())).isEqualTo(SonarCloudRegion.EU);
    assertThat(new SonarCloudActiveEnvironment(empty, regionWithBaseUri)
      .getRegionOrThrow(baseUri.toString())).isEqualTo(SonarCloudRegion.US);
  }
}
