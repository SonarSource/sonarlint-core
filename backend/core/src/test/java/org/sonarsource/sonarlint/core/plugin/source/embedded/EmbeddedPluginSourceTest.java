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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddedPluginSourceTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  // --- forConnected() ---

  @Test
  void list_AvailablePlugins_should_return_active_embedded_in_connected_mode_when_plugin_key_is_in_map() throws IOException {
    var javaJar = createJar("sonar-java-plugin.jar");
    var source = EmbeddedPluginSource.forConnected(mockParams(Set.of(), Map.of(SonarPlugin.JAVA.getKey(), javaJar)));

    var result = source.listAvailableArtifacts(Set.of());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).key()).isEqualTo(SonarPlugin.JAVA.getKey());
  }

  @Test
  void list_AvailablePlugins_should_return_empty_in_connected_mode_when_plugin_key_is_absent() {
    var source = EmbeddedPluginSource.forConnected(mockParams(Set.of(), Map.of()));

    assertThat(source.listAvailableArtifacts(Set.of())).isEmpty();
  }

  @Test
  void list_AvailablePlugins_should_include_companion_in_connected_mode_when_present_in_connected_mode_embedded_paths() throws IOException {
    var omnisharpJar = createJar("sonarlint-omnisharp-plugin.jar", "omnisharp");
    var source = EmbeddedPluginSource.forConnected(mockParams(Set.of(), Map.of("omnisharp", omnisharpJar)));

    var result = source.listAvailableArtifacts(Set.of());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).key()).isEqualTo("omnisharp");
  }

  // --- forStandalone() ---

  @Test
  void list_AvailablePlugins_should_return_active_embedded_in_standalone_when_jar_contains_plugin_key_manifest() throws IOException {
    var javaJar = createJar("sonar-java-plugin.jar", "java");
    var source = EmbeddedPluginSource.forStandalone(mockParams(Set.of(javaJar), Map.of()));

    var result = source.listAvailableArtifacts(Set.of());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).key()).isEqualTo("java");
  }

  @Test
  void list_AvailablePlugins_should_return_empty_in_standalone_when_embedded_paths_are_empty() {
    var source = EmbeddedPluginSource.forStandalone(mockParams(Set.of(), Map.of()));

    assertThat(source.listAvailableArtifacts(Set.of())).isEmpty();
  }

  @Test
  void list_AvailablePlugins_should_throw_when_duplicate_plugin_keys_are_found() throws IOException {
    var javaJar1 = createJar("sonar-java-plugin.jar", "java");
    var javaJar2 = createJar("sonar-java-plugin-2.jar", "java");
    var params = mockParams(Set.of(javaJar1, javaJar2), Map.of());

    assertThatThrownBy(() -> EmbeddedPluginSource.forStandalone(params))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Multiple embedded plugins found with the same key for paths");
  }

  // --- Companion plugins are included naturally in list() ---

  @Test
  void list_AvailablePlugins_should_include_companion_plugins_in_standalone_along_with_language_plugins() throws IOException {
    var omnisharpJar = createJar("sonarlint-omnisharp-plugin.jar", "omnisharp");
    var javaJar = createJar("sonar-java-plugin.jar", "java");
    var source = EmbeddedPluginSource.forStandalone(mockParams(Set.of(omnisharpJar, javaJar), Map.of()));

    var result = source.listAvailableArtifacts(Set.of());

    assertThat(result).hasSize(2);
    assertThat(result.stream().map(AvailableArtifact::key))
      .containsExactlyInAnyOrder("omnisharp", "java");
  }

  @Test
  void list_AvailablePlugins_should_not_include_html_plugin_key_when_manifest_says_web() throws IOException {
    var htmlJar = createJar("sonar-html-plugin.jar", "web");
    var source = EmbeddedPluginSource.forStandalone(mockParams(Set.of(htmlJar), Map.of()));

    var result = source.listAvailableArtifacts(Set.of());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).key()).isEqualTo("web");
  }

  private static InitializeParams mockParams(Set<Path> embeddedPaths, Map<String, Path> connectedPaths) {
    var params = mock(InitializeParams.class);
    when(params.getEmbeddedPluginPaths()).thenReturn(embeddedPaths);
    when(params.getConnectedModeEmbeddedPluginPathsByKey()).thenReturn(connectedPaths);
    return params;
  }

  private Path createJar(String name) throws IOException {
    return createJar(name, name.replace("sonar-", "").replaceAll("-plugin\\.jar|-oss\\.jar|-standalone\\.jar", ""));
  }

  private Path createJar(String name, String pluginKey) throws IOException {
    var path = tempDir.resolve(name);
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Plugin-Key", pluginKey);
    try (var jos = new JarOutputStream(Files.newOutputStream(path), manifest)) {
      // empty JAR with manifest
    }
    return path;
  }
}
