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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.plugin.ondemand.DownloadableArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;

public class EmbeddedExtraArtifactResolver implements ExtraArtifactResolver {

  private final Map<String, Path> paths;

  public EmbeddedExtraArtifactResolver(InitializeParams params) {
    this.paths = buildPaths(params.getLanguageSpecificRequirements());
  }

  @Override
  public Optional<Path> resolve(String artifactKey) {
    return Optional.ofNullable(paths.get(artifactKey));
  }

  private static Map<String, Path> buildPaths(@Nullable LanguageSpecificRequirements requirements) {
    if (requirements == null) {
      return Map.of();
    }
    var dto = requirements.getOmnisharpRequirements();
    if (dto == null) {
      return Map.of();
    }
    var result = new LinkedHashMap<String, Path>();
    addIfNotNull(result, DownloadableArtifact.OMNISHARP_MONO.artifactKey(), dto.getMonoDistributionPath());
    addIfNotNull(result, DownloadableArtifact.OMNISHARP_NET6.artifactKey(), dto.getDotNet6DistributionPath());
    addIfNotNull(result, DownloadableArtifact.OMNISHARP_WIN.artifactKey(), dto.getDotNet472DistributionPath());
    return Map.copyOf(result);
  }

  private static void addIfNotNull(Map<String, Path> map, String key, @Nullable Path value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
