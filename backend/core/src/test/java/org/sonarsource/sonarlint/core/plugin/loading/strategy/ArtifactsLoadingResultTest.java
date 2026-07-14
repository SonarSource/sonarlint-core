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
package org.sonarsource.sonarlint.core.plugin.loading.strategy;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPluginDependency;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactsLoadingResultTest {

  @Test
  void getPluginPaths_should_keep_omnisharp_when_enterprise_csharp_dependency_is_present() {
    var omnisharpPath = Path.of("omnisharp.jar");
    var result = new ArtifactsLoadingResult(Set.of(), Map.of(
      SonarPlugin.SONARLINT_OMNISHARP.getKey(), activeArtifact(omnisharpPath),
      SonarPlugin.CSHARP_ENTERPRISE.getKey(), activeArtifact(Path.of("csharpenterprise.jar")),
      SonarPluginDependency.OMNISHARP_MONO.getKey(), activeArtifact(Path.of("omnisharp-mono.tar.gz")),
      SonarPluginDependency.OMNISHARP_NET472.getKey(), activeArtifact(Path.of("omnisharp-net472.tar.gz")),
      SonarPluginDependency.OMNISHARP_NET6.getKey(), activeArtifact(Path.of("omnisharp-net6.tar.gz"))));

    assertThat(result.getPluginPaths()).contains(omnisharpPath);
  }

  @Test
  void getPluginPaths_should_remove_omnisharp_when_neither_csharp_dependency_is_present() {
    var omnisharpPath = Path.of("omnisharp.jar");
    var result = new ArtifactsLoadingResult(Set.of(), Map.of(
      SonarPlugin.SONARLINT_OMNISHARP.getKey(), activeArtifact(omnisharpPath),
      SonarPluginDependency.OMNISHARP_MONO.getKey(), activeArtifact(Path.of("omnisharp-mono.tar.gz")),
      SonarPluginDependency.OMNISHARP_NET472.getKey(), activeArtifact(Path.of("omnisharp-net472.tar.gz")),
      SonarPluginDependency.OMNISHARP_NET6.getKey(), activeArtifact(Path.of("omnisharp-net6.tar.gz"))));

    assertThat(result.getPluginPaths()).isEmpty();
  }

  private static ResolvedArtifact activeArtifact(Path path) {
    return new ResolvedArtifact(ArtifactState.ACTIVE, path, ArtifactOrigin.ON_DEMAND, null);
  }
}
