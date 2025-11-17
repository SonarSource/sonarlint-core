/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Strings;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarQubeCloudRegionDto;


public class SonarCloudActiveEnvironment {
  private final Map<SonarCloudRegion, SonarQubeCloudRegionDto> alternativeRegionUris;

  public static SonarCloudActiveEnvironment prod() {
    return new SonarCloudActiveEnvironment(Map.of());
  }

  public SonarCloudActiveEnvironment(Map<org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion, SonarQubeCloudRegionDto> alternativeRegionUris) {
    this.alternativeRegionUris = alternativeRegionUris.entrySet().stream()
      .collect(Collectors.toMap(entry -> SonarCloudRegion.valueOf(entry.getKey().name()), Map.Entry::getValue));
  }

  public URI getUri(SonarCloudRegion region) {
    if (alternativeRegionUris.containsKey(region) && alternativeRegionUris.get(region).getUri() != null) {
      return alternativeRegionUris.get(region).getUri();
    }
    return region.getProductionUri();
  }

  public URI getApiUri(SonarCloudRegion region) {
    if (alternativeRegionUris.containsKey(region) && alternativeRegionUris.get(region).getApiUri() != null) {
      return alternativeRegionUris.get(region).getApiUri();
    }
    return region.getApiProductionUri();
  }

  public URI getWebSocketsEndpointUri(SonarCloudRegion region) {
    if (alternativeRegionUris.containsKey(region) && alternativeRegionUris.get(region).getWebSocketsEndpointUri() != null) {
      return alternativeRegionUris.get(region).getWebSocketsEndpointUri();
    }
    return region.getWebSocketUri();
  }

  public boolean isSonarQubeCloud(String uri) {
    return getRegionByUri(uri).isPresent();
  }

  /**
   *  Before calling this method, caller should make sure URI is SonarCloud
   */
  public SonarCloudRegion getRegionOrThrow(String uri) {
    var regionOpt = getRegionByUri(uri);
    if (regionOpt.isPresent()) {
      return regionOpt.get();
    }
    
    throw new IllegalArgumentException("URI should be a known SonarCloud URI");
  }

  private Optional<SonarCloudRegion> getRegionByUri(String uri) {
    var cleanedUri = Strings.CS.removeEnd(uri, "/");
    for (var entry : alternativeRegionUris.entrySet()) {
      var regionUri = entry.getValue().getUri();
      if (regionUri != null && Strings.CS.removeEnd(regionUri.toString(), "/").equals(cleanedUri)) {
        return Optional.of(entry.getKey());
      }
    }

    for (var region : SonarCloudRegion.values()) {
      if (Strings.CS.removeEnd(region.getProductionUri().toString(), "/").equals(cleanedUri)) {
        return Optional.of(region);
      }
    }
    
    return Optional.empty();
  }
}
