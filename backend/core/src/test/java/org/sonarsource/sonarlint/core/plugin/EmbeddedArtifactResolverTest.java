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
package org.sonarsource.sonarlint.core.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedArtifactResolver;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddedArtifactResolverTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  // --- Connected mode ---

  @Test
  void should_resolve_to_active_embedded_in_connected_mode_when_plugin_key_is_in_map() throws IOException {
    var javaJar = createJar("sonar-java-plugin.jar");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(), Map.of(SonarLanguage.JAVA.getPluginKey(), javaJar), null));
    var expected = resolved(ArtifactState.ACTIVE, javaJar, ArtifactSource.EMBEDDED);

    var result = resolver.resolve(SonarLanguage.JAVA, "someConnection");

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_empty_in_connected_mode_when_plugin_key_is_absent() {
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(), Map.of(), null));

    var result = resolver.resolve(SonarLanguage.JAVA, "someConnection");

    assertThat(result).isEmpty();
  }

  // --- Standalone mode ---

  @Test
  void should_resolve_to_active_embedded_in_standalone_when_filename_contains_plugin_key() throws IOException {
    var javaJar = createJar("sonar-java-plugin.jar");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(javaJar), Map.of(), null));
    var expected = resolved(ArtifactState.ACTIVE, javaJar, ArtifactSource.EMBEDDED);

    var result = resolver.resolve(SonarLanguage.JAVA, null);

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_empty_in_standalone_when_no_file_matches_plugin_key() throws IOException {
    var otherJar = createJar("sonar-python-plugin.jar");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(otherJar), Map.of(), null));

    var result = resolver.resolve(SonarLanguage.JAVA, null);

    assertThat(result).isEmpty();
  }

  @Test
  void should_use_dedicated_csharp_path_in_standalone_when_no_file_match() throws IOException {
    var csharpPath = createJar("sonar-csharp-oss.jar");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(), Map.of(), csharpPath));
    var expected = resolved(ArtifactState.ACTIVE, csharpPath, ArtifactSource.EMBEDDED);

    var result = resolver.resolve(SonarLanguage.CS, null);

    assertThat(result).contains(expected);
  }

  @Test
  void should_prefer_file_match_over_dedicated_csharp_path_in_standalone() throws IOException {
    var matchingJar = createJar("sonar-csharp-plugin.jar");
    var dedicatedPath = createJar("sonar-csharp-standalone.jar");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(matchingJar), Map.of(), dedicatedPath));
    var expected = resolved(ArtifactState.ACTIVE, matchingJar, ArtifactSource.EMBEDDED);

    var result = resolver.resolve(SonarLanguage.CS, null);

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_empty_for_csharp_in_standalone_when_no_file_match_and_no_dedicated_path() {
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(), Map.of(), null));

    var result = resolver.resolve(SonarLanguage.CS, null);

    assertThat(result).isEmpty();
  }

  @Test
  void should_resolve_html_plugin_in_standalone_when_jar_name_differs_from_plugin_key() throws IOException {
    // HTML language has plugin key "web", but the JAR is named "sonar-html-plugin.jar"
    var htmlJar = createJar("sonar-html-plugin.jar", "web");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(htmlJar), Map.of(), null));
    var expected = resolved(ArtifactState.ACTIVE, htmlJar, ArtifactSource.EMBEDDED);

    var result = resolver.resolve(SonarLanguage.HTML, null);

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_empty_in_standalone_when_embedded_paths_are_empty() {
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(), Map.of(), null));

    var result = resolver.resolve(SonarLanguage.PYTHON, null);

    assertThat(result).isEmpty();
  }

  // --- Companion plugins ---

  @Test
  void should_return_only_non_language_plugin_keys_as_companion_plugins() throws IOException {
    var omnisharpJar = createJar("sonarlint-omnisharp-plugin.jar", "omnisharp");
    var javaJar = createJar("sonar-java-plugin.jar");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(omnisharpJar, javaJar), Map.of(), null));
    var expected = PluginStatus.forCompanion("omnisharp", ArtifactState.ACTIVE, ArtifactSource.EMBEDDED, omnisharpJar);

    var result = resolver.resolveCompanionPlugins(null);

    assertThat(result).containsExactly(Map.entry("omnisharp", expected));
  }

  @Test
  void should_return_empty_companion_plugins_in_connected_mode_when_companion_is_only_in_standalone() throws IOException {
    var javaSymExecJar = createJar("sonarlint-java-symbolic-execution-plugin.jar", "javasymbolicexecution");
    var javaJar = createJar("sonar-java-plugin.jar");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(javaSymExecJar, javaJar), Map.of(), null));

    assertThat(resolver.resolveCompanionPlugins("someConnection")).isEmpty();
  }

  @Test
  void should_return_companion_plugins_in_connected_mode_when_present_in_connected_mode_embedded_paths() throws IOException {
    var omnisharpJar = createJar("sonarlint-omnisharp-plugin.jar", "omnisharp");
    var javaJar = createJar("sonar-java-plugin.jar");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(omnisharpJar, javaJar), Map.of("omnisharp", omnisharpJar), null));
    var expected = PluginStatus.forCompanion("omnisharp", ArtifactState.ACTIVE, ArtifactSource.EMBEDDED, omnisharpJar);

    var result = resolver.resolveCompanionPlugins("someConnection");

    assertThat(result).containsExactly(Map.entry("omnisharp", expected));
  }

  @Test
  void should_return_empty_companion_plugins_when_all_plugins_are_language_plugins() throws IOException {
    var javaJar = createJar("sonar-java-plugin.jar");
    var pythonJar = createJar("sonar-python-plugin.jar");
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(javaJar, pythonJar), Map.of(), null));

    assertThat(resolver.resolveCompanionPlugins(null)).isEmpty();
  }

  @Test
  void should_return_empty_companion_plugins_when_embedded_paths_are_empty() {
    var resolver = new EmbeddedArtifactResolver(mockParams(Set.of(), Map.of(), null));

    assertThat(resolver.resolveCompanionPlugins(null)).isEmpty();
  }

  private static InitializeParams mockParams(Set<Path> embeddedPaths, Map<String, Path> connectedPaths, @Nullable Path csharpOssPath) {
    var params = mock(InitializeParams.class);
    when(params.getEmbeddedPluginPaths()).thenReturn(embeddedPaths);
    when(params.getConnectedModeEmbeddedPluginPathsByKey()).thenReturn(connectedPaths);
    if (csharpOssPath != null) {
      var languageRequirements = mock(LanguageSpecificRequirements.class);
      var omnisharpRequirements = mock(OmnisharpRequirementsDto.class);
      when(params.getLanguageSpecificRequirements()).thenReturn(languageRequirements);
      when(languageRequirements.getOmnisharpRequirements()).thenReturn(omnisharpRequirements);
      when(omnisharpRequirements.getOssAnalyzerPath()).thenReturn(csharpOssPath);
    } else {
      when(params.getLanguageSpecificRequirements()).thenReturn(null);
    }
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

  private static ResolvedArtifact resolved(ArtifactState state, Path path, ArtifactSource source) {
    return new ResolvedArtifact(state, path, source, null);
  }
}
