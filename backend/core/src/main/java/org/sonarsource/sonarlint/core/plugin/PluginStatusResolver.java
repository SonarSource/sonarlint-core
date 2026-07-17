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
package org.sonarsource.sonarlint.core.plugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactsLoadingResult;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;

final class PluginStatusResolver {
  private PluginStatusResolver() {
  }

  static List<PluginStatus> from(ArtifactsLoadingResult result) {
    return Arrays.stream(SonarLanguage.values())
      .map(language -> buildPluginStatus(language, result))
      .toList();
  }

  private static PluginStatus buildPluginStatus(SonarLanguage language, ArtifactsLoadingResult result) {
    var pluginKey = resolvePluginKey(language, result.resolvedArtifactsByKey());
    return result.getResolvedArtifactByKey(pluginKey)
      .map(artifact -> PluginStatus.forLanguage(language, artifact.state(), artifact.source(), artifact.version(), null, artifact.path(), null))
      .orElseGet(() -> PluginStatus.unsupported(language));
  }

  private static String resolvePluginKey(SonarLanguage language, Map<String, ResolvedArtifact> resolved) {
    var baseKey = language.getPlugin().getKey();
    var enterpriseKeys = SonarPlugin.findByKey(baseKey)
      .map(SonarPlugin::getEnterpriseVariants)
      .map(variants -> variants.stream().map(SonarPlugin::getKey).collect(Collectors.toSet()))
      .orElseGet(Set::of);
    return enterpriseKeys.stream()
      .filter(resolved::containsKey)
      .findFirst()
      .orElse(baseKey);
  }
}
