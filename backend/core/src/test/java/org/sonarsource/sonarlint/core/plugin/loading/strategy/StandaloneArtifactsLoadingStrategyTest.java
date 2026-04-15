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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPluginDependency;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;
import org.sonarsource.sonarlint.core.plugin.source.LoadResult;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StandaloneArtifactsLoadingStrategyTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private BinariesArtifactSource binariesSource;
  private LanguageSupportRepository languageSupportRepository;
  private InitializeParams params;

  @BeforeEach
  void setUp() {
    binariesSource = mock(BinariesArtifactSource.class);
    languageSupportRepository = mock(LanguageSupportRepository.class);
    params = mock(InitializeParams.class);
    when(params.getEmbeddedPluginPaths()).thenReturn(java.util.Set.of());
    when(params.getConnectedModeEmbeddedPluginPathsByKey()).thenReturn(Map.of());
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of());
    when(binariesSource.load(any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") var keys = (Set<String>) inv.getArgument(0);
      return new LoadResult(keys.stream().collect(Collectors.toMap(k -> k,
        k -> new ResolvedArtifact(ArtifactState.ACTIVE, null, ArtifactOrigin.ON_DEMAND, null, null))));
    });
    when(languageSupportRepository.getEnabledLanguagesInStandaloneMode()).thenReturn(EnumSet.noneOf(SonarLanguage.class));
    when(languageSupportRepository.isEnabledOnlyInConnectedMode(any())).thenReturn(false);
  }

  // --- Dependency removal (no dependent available) ---

  @Test
  void resolvePlugins_should_remove_dependency_when_dependent_plugin_is_not_available() {
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of(
      new AvailableArtifact(SonarPluginDependency.OMNISHARP_MONO.getKey(), null, false, Optional.of(SonarPluginDependency.OMNISHARP_MONO))));
    var strategy = createStrategy();

    var result = strategy.resolveArtifacts();

    assertThat(result.resolvedArtifactsByKey()).doesNotContainKey(SonarPluginDependency.OMNISHARP_MONO.getKey());
  }

  @Test
  void resolvePlugins_should_keep_dependency_when_dependent_plugin_is_available() {
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of(
      new AvailableArtifact(SonarPlugin.SONARLINT_OMNISHARP.getKey(), null, false, Optional.of(SonarPlugin.SONARLINT_OMNISHARP)),
      new AvailableArtifact(SonarPlugin.CS_OSS.getKey(), null, false, Optional.of(SonarPlugin.CS_OSS)),
      new AvailableArtifact(SonarPluginDependency.OMNISHARP_MONO.getKey(), null, false, Optional.of(SonarPluginDependency.OMNISHARP_MONO)),
      new AvailableArtifact(SonarPluginDependency.OMNISHARP_NET472.getKey(), null, false, Optional.of(SonarPluginDependency.OMNISHARP_NET472)),
      new AvailableArtifact(SonarPluginDependency.OMNISHARP_NET6.getKey(), null, false, Optional.of(SonarPluginDependency.OMNISHARP_NET6))));
    var strategy = createStrategy();

    var result = strategy.resolveArtifacts();

    assertThat(result.resolvedArtifactsByKey()).containsKey(SonarPluginDependency.OMNISHARP_MONO.getKey());
  }

  // --- Plugin removal when required dependency is missing ---

  @Test
  void resolvePlugins_should_remove_plugin_when_a_required_dependency_is_not_available() {
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of(
      new AvailableArtifact(SonarPlugin.SONARLINT_OMNISHARP.getKey(), null, false, Optional.of(SonarPlugin.SONARLINT_OMNISHARP))));
    var strategy = createStrategy();

    var result = strategy.resolveArtifacts();

    assertThat(result.resolvedArtifactsByKey()).doesNotContainKey(SonarPlugin.SONARLINT_OMNISHARP.getKey());
  }

  @Test
  void resolvePlugins_should_keep_plugin_when_all_required_dependencies_are_available() {
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of(
      new AvailableArtifact(SonarPlugin.SONARLINT_OMNISHARP.getKey(), null, false, Optional.of(SonarPlugin.SONARLINT_OMNISHARP)),
      new AvailableArtifact(SonarPlugin.CS_OSS.getKey(), null, false, Optional.of(SonarPlugin.CS_OSS)),
      new AvailableArtifact(SonarPluginDependency.OMNISHARP_MONO.getKey(), null, false, Optional.of(SonarPluginDependency.OMNISHARP_MONO)),
      new AvailableArtifact(SonarPluginDependency.OMNISHARP_NET472.getKey(), null, false, Optional.of(SonarPluginDependency.OMNISHARP_NET472)),
      new AvailableArtifact(SonarPluginDependency.OMNISHARP_NET6.getKey(), null, false, Optional.of(SonarPluginDependency.OMNISHARP_NET6))));
    var strategy = createStrategy();

    var result = strategy.resolveArtifacts();

    assertThat(result.resolvedArtifactsByKey()).containsKey(SonarPlugin.SONARLINT_OMNISHARP.getKey());
  }

  private StandaloneArtifactsLoadingStrategy createStrategy() {
    return new StandaloneArtifactsLoadingStrategy(params, binariesSource, languageSupportRepository);
  }
}
