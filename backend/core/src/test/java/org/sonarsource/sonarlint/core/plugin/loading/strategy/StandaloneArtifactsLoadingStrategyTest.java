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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.plugins.SonarArtifact;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPluginDependency;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;
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
    when(languageSupportRepository.getEnabledLanguagesInStandaloneMode()).thenReturn(EnumSet.noneOf(SonarLanguage.class));
    when(languageSupportRepository.isEnabledOnlyInConnectedMode(any())).thenReturn(false);
  }

  // --- Enterprise deduplication (different-key variants: CS, VBNET) ---

  @Test
  void resolvePlugins_should_remove_base_key_when_enterprise_variant_is_present() {
    // Both the OSS base and the enterprise variant are available (e.g. base from binaries,
    // enterprise from embedded). Only the enterprise variant should survive.
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of(
      available(SonarPlugin.CS_OSS.getKey(), false, Optional.of(SonarPlugin.CS_OSS)),
      available(SonarPlugin.CSHARP_ENTERPRISE.getKey(), true, Optional.of(SonarPlugin.CSHARP_ENTERPRISE))));
    var strategy = createStrategy();

    var result = strategy.planArtifacts();

    assertThat(result.selectedArtifacts())
      .containsKey(SonarPlugin.CSHARP_ENTERPRISE.getKey())
      .doesNotContainKey(SonarPlugin.CS_OSS.getKey());
  }

  // --- Dependency removal (no dependent available) ---

  @Test
  void resolvePlugins_should_remove_dependency_when_dependent_plugin_is_not_available() {
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of(
      available(SonarPluginDependency.OMNISHARP_MONO.getKey(), false, Optional.of(SonarPluginDependency.OMNISHARP_MONO))));
    var strategy = createStrategy();

    var result = strategy.planArtifacts();

    assertThat(result.selectedArtifacts()).doesNotContainKey(SonarPluginDependency.OMNISHARP_MONO.getKey());
  }

  @Test
  void resolvePlugins_should_keep_dependency_when_dependent_plugin_is_available() {
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of(
      available(SonarPlugin.SONARLINT_OMNISHARP.getKey(), false, Optional.of(SonarPlugin.SONARLINT_OMNISHARP)),
      available(SonarPlugin.CS_OSS.getKey(), false, Optional.of(SonarPlugin.CS_OSS)),
      available(SonarPluginDependency.OMNISHARP_MONO.getKey(), false, Optional.of(SonarPluginDependency.OMNISHARP_MONO)),
      available(SonarPluginDependency.OMNISHARP_NET472.getKey(), false, Optional.of(SonarPluginDependency.OMNISHARP_NET472)),
      available(SonarPluginDependency.OMNISHARP_NET6.getKey(), false, Optional.of(SonarPluginDependency.OMNISHARP_NET6))));
    var strategy = createStrategy();

    var result = strategy.planArtifacts();

    assertThat(result.selectedArtifacts()).containsKey(SonarPluginDependency.OMNISHARP_MONO.getKey());
  }

  // --- Plugin removal when required dependency is missing ---

  @Test
  void resolvePlugins_should_remove_plugin_when_a_required_dependency_is_not_available() {
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of(
      available(SonarPlugin.SONARLINT_OMNISHARP.getKey(), false, Optional.of(SonarPlugin.SONARLINT_OMNISHARP))));
    var strategy = createStrategy();

    var result = strategy.planArtifacts();

    assertThat(result.selectedArtifacts()).doesNotContainKey(SonarPlugin.SONARLINT_OMNISHARP.getKey());
  }

  @Test
  void resolvePlugins_should_keep_plugin_when_all_required_dependencies_are_available() {
    when(binariesSource.listAvailableArtifacts(any())).thenReturn(List.of(
      available(SonarPlugin.SONARLINT_OMNISHARP.getKey(), false, Optional.of(SonarPlugin.SONARLINT_OMNISHARP)),
      available(SonarPlugin.CS_OSS.getKey(), false, Optional.of(SonarPlugin.CS_OSS)),
      available(SonarPluginDependency.OMNISHARP_MONO.getKey(), false, Optional.of(SonarPluginDependency.OMNISHARP_MONO)),
      available(SonarPluginDependency.OMNISHARP_NET472.getKey(), false, Optional.of(SonarPluginDependency.OMNISHARP_NET472)),
      available(SonarPluginDependency.OMNISHARP_NET6.getKey(), false, Optional.of(SonarPluginDependency.OMNISHARP_NET6))));
    var strategy = createStrategy();

    var result = strategy.planArtifacts();

    assertThat(result.selectedArtifacts()).containsKey(SonarPlugin.SONARLINT_OMNISHARP.getKey());
  }

  private StandaloneArtifactsLoadingStrategy createStrategy() {
    return new StandaloneArtifactsLoadingStrategy(params, binariesSource, languageSupportRepository);
  }

  private static AvailableArtifact available(String key, boolean enterprise, Optional<? extends SonarArtifact> sonarArtifact) {
    return new AvailableArtifact(key, null, enterprise, sonarArtifact,
      new ArtifactLocation.Remote(mock(org.sonarsource.sonarlint.core.plugin.source.ArtifactDownload.class)));
  }
}
