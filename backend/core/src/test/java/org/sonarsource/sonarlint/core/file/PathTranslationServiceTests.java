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
package org.sonarsource.sonarlint.core.file;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PathTranslationServiceTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String CONFIG_SCOPE = "configScopeA";
  private static final Binding BINDING = new Binding("connectionA", "sonarProjectA");
  private final ClientFileSystemService clientFs = mock(ClientFileSystemService.class);
  private final ConfigurationRepository configurationRepository = mock(ConfigurationRepository.class);
  private final ServerFilePathsProvider serverFilePathsProvider = mock(ServerFilePathsProvider.class);
  private final PathTranslationService underTest = new PathTranslationService(clientFs, configurationRepository, serverFilePathsProvider);

  @BeforeEach
  void prepare() {
    when(configurationRepository.getEffectiveBinding(CONFIG_SCOPE)).thenReturn(Optional.of(BINDING));
  }

  @Test
  void shouldComputePathTranslations() {
    mockServerFilePaths(BINDING, "moduleA/src/Foo.java");
    mockClientFilePaths("src/Foo.java");

    var result = underTest.getOrComputePathTranslation(CONFIG_SCOPE);

    assertThat(result).isPresent();
    assertThat(result.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleA")));
  }

  private void mockServerFilePaths(Binding binding, String... paths) {
    when(serverFilePathsProvider.getServerPaths(eq(binding), any(SonarLintCancelMonitor.class)))
      .thenReturn(Optional.of(Arrays.stream(paths).map(Paths::get).toList()));
  }

  @Test
  void shouldCachePathTranslations() {
    mockServerFilePaths(BINDING, "moduleA/src/Foo.java");
    mockClientFilePaths("src/Foo.java");

    var result1 = underTest.getOrComputePathTranslation(CONFIG_SCOPE);

    assertThat(result1).isPresent();
    assertThat(result1.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleA")));

    var result2 = underTest.getOrComputePathTranslation(CONFIG_SCOPE);

    assertThat(result2).isPresent();
    assertThat(result2.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleA")));

    verify(clientFs, times(1)).getFiles(any());
  }

  @Test
  void shouldRecomputePathTranslationsAfterBindingChange() {
    mockServerFilePaths(BINDING, "moduleA/src/Foo.java");
    mockClientFilePaths("src/Foo.java");

    var result1 = underTest.getOrComputePathTranslation(CONFIG_SCOPE);

    assertThat(result1).isPresent();
    assertThat(result1.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleA")));

    Binding newBinding = mock(Binding.class);
    when(configurationRepository.getEffectiveBinding(CONFIG_SCOPE)).thenReturn(Optional.of(newBinding));
    mockServerFilePaths(newBinding, "moduleB/src/Foo.java");

    underTest.onBindingChanged(new BindingConfigChangedEvent(CONFIG_SCOPE, null, null));

    var result2 = underTest.getOrComputePathTranslation(CONFIG_SCOPE);

    assertThat(result2).isPresent();
    assertThat(result2.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleB")));
  }

  private void mockClientFilePaths(String... paths) {
    doReturn(Arrays.stream(paths)
      .map(path -> new ClientFile(null, null, Paths.get(path), null, null, null, null, true))
      .toList())
      .when(clientFs)
      .getFiles(CONFIG_SCOPE);
  }
}
