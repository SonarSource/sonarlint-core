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
package org.sonarsource.sonarlint.core.plugin.source.embedded;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.commons.loading.SonarPluginManifest;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;

/**
 * Artifact source backed by JARs physically bundled (embedded) in the IDE client's distribution.
 * Returns both language plugins and companion plugins. No downloads are ever triggered.
 *
 * <p>Use {@link #forStandalone(InitializeParams)} or {@link #forConnected(InitializeParams)} to
 * obtain an instance scoped to the appropriate mode.</p>
 */
public class EmbeddedPluginSource implements ArtifactSource {

  public static final String CSHARP_PLUGIN_KEY = SonarPlugin.CS_OSS.getKey();
  private final Map<String, Path> embeddedPathsByKey;
  @Nullable
  private final Path csharpStandalonePluginPath;

  private EmbeddedPluginSource(Map<String, Path> embeddedPathsByKey, @Nullable Path csharpStandalonePluginPath) {
    this.embeddedPathsByKey = embeddedPathsByKey;
    this.csharpStandalonePluginPath = csharpStandalonePluginPath;
  }

  /**
   * Returns a source backed by the standalone embedded plugin paths from {@code params}, including
   * the optional standalone C# OSS analyzer.
   */
  public static EmbeddedPluginSource forStandalone(InitializeParams params) {
    var pathsByKey = buildPluginKeyToPathMap(params.getEmbeddedPluginPaths());
    var csharpStandalonePluginPath = Optional.ofNullable(params.getLanguageSpecificRequirements())
      .map(LanguageSpecificRequirements::getOmnisharpRequirements)
      .map(OmnisharpRequirementsDto::getOssAnalyzerPath)
      .orElse(null);
    return new EmbeddedPluginSource(pathsByKey, csharpStandalonePluginPath);
  }

  /**
   * Returns a source backed by the connected-mode embedded plugin paths from {@code params}.
   * The standalone C# OSS analyzer is not included.
   */
  public static EmbeddedPluginSource forConnected(InitializeParams params) {
    return new EmbeddedPluginSource(params.getConnectedModeEmbeddedPluginPathsByKey(), null);
  }

  /**
   * Returns all artifacts physically embedded in the IDE client. No downloads are ever triggered.
   * We ignore the enabledLanguages parameter for this source. We trust the clients to provide sensible embedded artifacts.
   */
  @Override
  public List<AvailableArtifact> listAvailableArtifacts(Set<SonarLanguage> enabledLanguages) {
    var result = new ArrayList<AvailableArtifact>();
    for (var entry : embeddedPathsByKey.entrySet()) {
      result.add(toAvailableArtifact(entry.getKey(), entry.getValue()));
    }
    if (csharpStandalonePluginPath != null && !embeddedPathsByKey.containsKey(CSHARP_PLUGIN_KEY)) {
      result.add(toAvailableArtifact(CSHARP_PLUGIN_KEY, csharpStandalonePluginPath));
    }
    return result;
  }

  @Override
  public Optional<ResolvedArtifact> load(String artifactKey) {
    var path = embeddedPathsByKey.get(artifactKey);
    if (path == null && CSHARP_PLUGIN_KEY.equals(artifactKey) && csharpStandalonePluginPath != null
        && !embeddedPathsByKey.containsKey(CSHARP_PLUGIN_KEY)) {
      path = csharpStandalonePluginPath;
    }
    if (path == null) {
      return Optional.empty();
    }
    return Optional.of(new ResolvedArtifact(ArtifactState.ACTIVE, path, ArtifactOrigin.EMBEDDED, PluginJarUtils.readVersion(path), null));
  }

  private static AvailableArtifact toAvailableArtifact(String key, Path path) {
    return new AvailableArtifact(key, PluginJarUtils.readVersion(path));
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
