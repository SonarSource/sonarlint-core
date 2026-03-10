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

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.plugin.ondemand.DownloadableArtifact;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedExtraArtifactResolver;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddedExtraArtifactResolverTest {

  private static final Path MONO_PATH = Paths.get("omnisharp", "mono");
  private static final Path NET6_PATH = Paths.get("omnisharp", "net6");
  private static final Path NET472_PATH = Paths.get("omnisharp", "net472");

  @Test
  void should_return_empty_for_all_keys_when_language_specific_requirements_are_null() {
    var params = mock(InitializeParams.class);
    when(params.getLanguageSpecificRequirements()).thenReturn(null);
    var resolver = new EmbeddedExtraArtifactResolver(params);

    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_MONO.artifactKey())).isEmpty();
    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_NET6.artifactKey())).isEmpty();
    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_WIN.artifactKey())).isEmpty();
  }

  @Test
  void should_return_empty_for_all_keys_when_omnisharp_requirements_are_null() {
    var resolver = new EmbeddedExtraArtifactResolver(mockParams(null));

    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_MONO.artifactKey())).isEmpty();
    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_NET6.artifactKey())).isEmpty();
    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_WIN.artifactKey())).isEmpty();
  }

  @Test
  void should_resolve_all_keys_when_all_paths_are_configured() {
    var dto = new OmnisharpRequirementsDto(MONO_PATH, NET6_PATH, NET472_PATH, null, null);
    var resolver = new EmbeddedExtraArtifactResolver(mockParams(dto));

    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_MONO.artifactKey())).contains(MONO_PATH);
    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_NET6.artifactKey())).contains(NET6_PATH);
    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_WIN.artifactKey())).contains(NET472_PATH);
  }

  @Test
  void should_resolve_only_mono_key_when_only_mono_path_is_configured() {
    var dto = new OmnisharpRequirementsDto(MONO_PATH, null, null, null, null);
    var resolver = new EmbeddedExtraArtifactResolver(mockParams(dto));

    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_MONO.artifactKey())).contains(MONO_PATH);
    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_NET6.artifactKey())).isEmpty();
    assertThat(resolver.resolve(DownloadableArtifact.OMNISHARP_WIN.artifactKey())).isEmpty();
  }

  @Test
  void should_return_empty_for_unknown_artifact_key() {
    var dto = new OmnisharpRequirementsDto(MONO_PATH, NET6_PATH, NET472_PATH, null, null);
    var resolver = new EmbeddedExtraArtifactResolver(mockParams(dto));

    var result = resolver.resolve("unknownKey");

    assertThat(result).isEmpty();
  }

  private static InitializeParams mockParams(OmnisharpRequirementsDto dto) {
    var params = mock(InitializeParams.class);
    when(params.getLanguageSpecificRequirements()).thenReturn(new LanguageSpecificRequirements(null, dto));
    return params;
  }
}
