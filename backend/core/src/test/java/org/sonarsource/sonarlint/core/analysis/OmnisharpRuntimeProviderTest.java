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
package org.sonarsource.sonarlint.core.analysis;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.plugin.resolvers.OmnisharpDistributionDownloader;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OmnisharpRuntimeProviderTest {

  private OmnisharpDistributionDownloader downloader;
  private InitializeParams initializeParams;

  @BeforeEach
  void setUp() {
    downloader = mock(OmnisharpDistributionDownloader.class);
    initializeParams = mock(InitializeParams.class);
  }

  @Test
  void should_return_empty_map_when_no_paths_available() {
    when(initializeParams.getLanguageSpecificRequirements()).thenReturn(null);
    when(downloader.getMonoPath()).thenReturn(null);
    when(downloader.getDotNet472Path()).thenReturn(null);
    when(downloader.getDotNet6Path()).thenReturn(null);

    var provider = new OmnisharpRuntimeProvider(initializeParams, downloader);

    assertThat(provider.getExtraProperties()).isEmpty();
  }

  @Test
  void should_return_downloaded_paths_when_no_client_paths_provided() {
    when(initializeParams.getLanguageSpecificRequirements()).thenReturn(null);
    var monoPath = Path.of("/downloaded/mono");
    var net472Path = Path.of("/downloaded/net472");
    var net6Path = Path.of("/downloaded/net6");
    when(downloader.getMonoPath()).thenReturn(monoPath);
    when(downloader.getDotNet472Path()).thenReturn(net472Path);
    when(downloader.getDotNet6Path()).thenReturn(net6Path);

    var provider = new OmnisharpRuntimeProvider(initializeParams, downloader);
    var properties = provider.getExtraProperties();

    assertThat(properties)
      .containsEntry("sonar.cs.internal.omnisharpMonoLocation", monoPath.toString())
      .containsEntry("sonar.cs.internal.omnisharpWinLocation", net472Path.toString())
      .containsEntry("sonar.cs.internal.omnisharpNet6Location", net6Path.toString());
  }

  @Test
  void should_prefer_client_paths_over_downloaded_paths() {
    var clientMono = Path.of("/client/mono");
    var clientNet472 = Path.of("/client/net472");
    var clientNet6 = Path.of("/client/net6");
    var requirements = mock(OmnisharpRequirementsDto.class);
    when(requirements.getMonoDistributionPath()).thenReturn(clientMono);
    when(requirements.getDotNet472DistributionPath()).thenReturn(clientNet472);
    when(requirements.getDotNet6DistributionPath()).thenReturn(clientNet6);
    var languageReqs = mock(LanguageSpecificRequirements.class);
    when(languageReqs.getOmnisharpRequirements()).thenReturn(requirements);
    when(initializeParams.getLanguageSpecificRequirements()).thenReturn(languageReqs);

    when(downloader.getMonoPath()).thenReturn(Path.of("/downloaded/mono"));
    when(downloader.getDotNet472Path()).thenReturn(Path.of("/downloaded/net472"));
    when(downloader.getDotNet6Path()).thenReturn(Path.of("/downloaded/net6"));

    var provider = new OmnisharpRuntimeProvider(initializeParams, downloader);
    var properties = provider.getExtraProperties();

    assertThat(properties)
      .containsEntry("sonar.cs.internal.omnisharpMonoLocation", clientMono.toString())
      .containsEntry("sonar.cs.internal.omnisharpWinLocation", clientNet472.toString())
      .containsEntry("sonar.cs.internal.omnisharpNet6Location", clientNet6.toString());
  }

  @Test
  void should_fall_back_to_downloaded_path_when_client_path_is_null_for_a_variant() {
    var clientMono = Path.of("/client/mono");
    var downloadedNet472 = Path.of("/downloaded/net472");
    var requirements = mock(OmnisharpRequirementsDto.class);
    when(requirements.getMonoDistributionPath()).thenReturn(clientMono);
    when(requirements.getDotNet472DistributionPath()).thenReturn(null);
    when(requirements.getDotNet6DistributionPath()).thenReturn(null);
    var languageReqs = mock(LanguageSpecificRequirements.class);
    when(languageReqs.getOmnisharpRequirements()).thenReturn(requirements);
    when(initializeParams.getLanguageSpecificRequirements()).thenReturn(languageReqs);

    when(downloader.getMonoPath()).thenReturn(Path.of("/downloaded/mono"));
    when(downloader.getDotNet472Path()).thenReturn(downloadedNet472);
    when(downloader.getDotNet6Path()).thenReturn(null);

    var provider = new OmnisharpRuntimeProvider(initializeParams, downloader);
    var properties = provider.getExtraProperties();

    assertThat(properties)
      .containsEntry("sonar.cs.internal.omnisharpMonoLocation", clientMono.toString())
      .containsEntry("sonar.cs.internal.omnisharpWinLocation", downloadedNet472.toString())
      .doesNotContainKey("sonar.cs.internal.omnisharpNet6Location");
  }
}
