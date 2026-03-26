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
package org.sonarsource.sonarlint.core.analysis;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.plugin.resolvers.OmnisharpDistributionDownloader;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;

/**
 * Resolves OmniSharp runtime paths for analysis configuration.
 *
 * <p>For each of the three OmniSharp variants (Mono, .NET 4.7.2, .NET 6), the client-provided path
 * (from {@link OmnisharpRequirementsDto}) takes priority. When the client provides no path for a
 * variant, the path downloaded asynchronously by {@link OmnisharpDistributionDownloader} is used
 * as a fallback.</p>
 *
 * <p>Callers should re-query {@link #getExtraProperties()} after an
 * {@link org.sonarsource.sonarlint.core.event.OmnisharpDistributionChangedEvent} to pick up
 * newly completed downloads.</p>
 */
public class OmnisharpRuntimeProvider {

  private final OmnisharpDistributionDownloader omnisharpDistributionDownloader;
  @Nullable
  private final OmnisharpRequirementsDto omnisharpRequirementsDto;

  public OmnisharpRuntimeProvider(InitializeParams initializeParams, OmnisharpDistributionDownloader omnisharpDistributionDownloader) {
    this.omnisharpDistributionDownloader = omnisharpDistributionDownloader;
    this.omnisharpRequirementsDto = Optional.ofNullable(initializeParams.getLanguageSpecificRequirements())
      .map(LanguageSpecificRequirements::getOmnisharpRequirements)
      .orElse(null);
  }

  /**
   * Returns analysis extra-property entries for the resolved OmniSharp runtime paths.
   * Only entries for variants with a resolved path are included.
   * Client-provided paths take priority over downloaded ones.
   */
  public Map<String, String> getExtraProperties() {
    var properties = new HashMap<String, String>();
    var monoPath = resolve(omnisharpDistributionDownloader.getMonoPath(),
      omnisharpRequirementsDto != null ? omnisharpRequirementsDto.getMonoDistributionPath() : null);
    var net472Path = resolve(omnisharpDistributionDownloader.getDotNet472Path(),
      omnisharpRequirementsDto != null ? omnisharpRequirementsDto.getDotNet472DistributionPath() : null);
    var net6Path = resolve(omnisharpDistributionDownloader.getDotNet6Path(),
      omnisharpRequirementsDto != null ? omnisharpRequirementsDto.getDotNet6DistributionPath() : null);
    if (monoPath != null) {
      properties.put("sonar.cs.internal.omnisharpMonoLocation", monoPath.toString());
    }
    if (net472Path != null) {
      properties.put("sonar.cs.internal.omnisharpWinLocation", net472Path.toString());
    }
    if (net6Path != null) {
      properties.put("sonar.cs.internal.omnisharpNet6Location", net6Path.toString());
    }
    return properties;
  }

  @Nullable
  private static Path resolve(@Nullable Path downloadedPath, @Nullable Path clientProvidedPath) {
    return OmnisharpDistributionDownloader.resolveWithFallback(downloadedPath, clientProvidedPath);
  }

}
