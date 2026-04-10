/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.PluginJarUtils;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.plugin.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.commons.loading.SonarPluginManifest;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;

/**
 * Resolves analyzers that are physically bundled (embedded) inside the SQ:IDE distribution.
 * It serves predefined JARs (like sonar-java-plugin.jar) from the plugin payload.
 * This acts as the ultimate fallback when no server connection or on-demand downloads are applicable.
 */
public class EmbeddedArtifactResolver implements ArtifactResolver, CompanionPluginResolver {

  private final Map<String, Path> standaloneEmbeddedPathsByKey;
  private final Map<String, Path> connectedModeEmbeddedPathsByKey;
  @Nullable
  private final Path csharpStandalonePluginPath;
  private Map<String, PluginStatus> standaloneCompanionPlugins;
  private Map<String, PluginStatus> connectedModeCompanionPlugins;

  public EmbeddedArtifactResolver(InitializeParams params) {
    this.standaloneEmbeddedPathsByKey = buildPluginKeyToPathMap(params.getEmbeddedPluginPaths());
    this.connectedModeEmbeddedPathsByKey = params.getConnectedModeEmbeddedPluginPathsByKey();
    this.csharpStandalonePluginPath = Optional.ofNullable(params.getLanguageSpecificRequirements())
      .map(LanguageSpecificRequirements::getOmnisharpRequirements)
      .map(OmnisharpRequirementsDto::getOssAnalyzerPath)
      .orElse(null);
  }

  @Override
  public Optional<ResolvedArtifact> resolve(SonarLanguage language, @Nullable String connectionId) {
    return Optional.ofNullable(resolvePath(language, connectionId))
      .map(EmbeddedArtifactResolver::toResolvedArtifact);
  }

  @Override
  public Map<String, PluginStatus> resolveCompanionPlugins(@Nullable String connectionId) {
    if (connectionId != null) {
      if (connectedModeCompanionPlugins == null) {
        connectedModeCompanionPlugins = computeCompanionPlugins(connectedModeEmbeddedPathsByKey);
      }
      return connectedModeCompanionPlugins;
    }
    if (standaloneCompanionPlugins == null) {
      standaloneCompanionPlugins = computeCompanionPlugins(standaloneEmbeddedPathsByKey);
    }
    return standaloneCompanionPlugins;
  }

  private static Map<String, PluginStatus> computeCompanionPlugins(Map<String, Path> pathsByKey) {
    return pathsByKey.entrySet().stream()
      .filter(e -> SonarPlugin.findByKey(e.getKey()).isEmpty())
      .collect(Collectors.toUnmodifiableMap(
        Map.Entry::getKey,
        e -> PluginStatus.forCompanion(e.getKey(), ArtifactState.ACTIVE, ArtifactOrigin.EMBEDDED, e.getValue(), null)));
  }

  @Nullable
  private Path resolvePath(SonarLanguage language, @Nullable String connectionId) {
    return connectionId != null ? resolveConnected(language) : resolveStandalone(language);
  }

  private static ResolvedArtifact toResolvedArtifact(Path path) {
    return new ResolvedArtifact(ArtifactState.ACTIVE, path, ArtifactOrigin.EMBEDDED, PluginJarUtils.readVersion(path));
  }

  @Nullable
  private Path resolveConnected(SonarLanguage language) {
    return connectedModeEmbeddedPathsByKey.get(language.getPlugin().getKey());
  }

  @Nullable
  private Path resolveStandalone(SonarLanguage language) {
    var found = standaloneEmbeddedPathsByKey.get(language.getPlugin().getKey());
    if (found == null && language == SonarLanguage.CS) {
      return csharpStandalonePluginPath;
    }
    return found;
  }

  private static Map<String, Path> buildPluginKeyToPathMap(Set<Path> embeddedPaths) {
    return embeddedPaths.stream()
      .collect(Collectors.toMap(
        p -> SonarPluginManifest.fromJar(p).getKey(),
        Function.identity(),
        (existing, duplicate) -> {
          throw new IllegalArgumentException("Multiple embedded plugins found with the same key for paths: " + existing + " and " + duplicate);
        }));
  }

}
