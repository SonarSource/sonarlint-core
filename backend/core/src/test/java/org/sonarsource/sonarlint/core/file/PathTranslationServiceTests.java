/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.component.ComponentApi;

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
  private final static SonarLintLogTester logTester = new SonarLintLogTester(true);
  public static final String CONFIG_SCOPE_A = "configScopeA";
  public static final String CONNECTION_A = "connectionA";
  public static final String SONAR_PROJECT_A = "sonarProjectA";
  private final ServerApiProvider serverApiProvider = mock(ServerApiProvider.class);
  private final ServerApi serverApi = mock(ServerApi.class);
  private final ClientFileSystemService clientFs = mock(ClientFileSystemService.class);
  private final ConfigurationRepository configurationRepository = mock(ConfigurationRepository.class);
  private final PathTranslationService underTest = new PathTranslationService(clientFs, serverApiProvider, configurationRepository);
  private final ComponentApi componentApi = mock(ComponentApi.class);

  @BeforeEach
  void prepare() {
    when(configurationRepository.getBoundScope(CONFIG_SCOPE_A)).thenReturn(new BoundScope(CONFIG_SCOPE_A, CONNECTION_A, SONAR_PROJECT_A));
    when(serverApiProvider.getServerApi(CONNECTION_A)).thenReturn(Optional.of(serverApi));
    when(serverApi.component()).thenReturn(componentApi);
  }

  @Test
  void shouldComputePathTranslations() {
    mockServerFilePaths(SONAR_PROJECT_A, "moduleA/src/Foo.java");
    mockClientFilePaths(CONFIG_SCOPE_A, "src/Foo.java");

    var result = underTest.getOrComputePathTranslation(CONFIG_SCOPE_A);

    assertThat(result).isPresent();
    assertThat(result.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleA")));
  }

  @Test
  void shouldCachePathTranslations() {
    mockServerFilePaths(SONAR_PROJECT_A, "moduleA/src/Foo.java");
    mockClientFilePaths(CONFIG_SCOPE_A, "src/Foo.java");

    var result1 = underTest.getOrComputePathTranslation(CONFIG_SCOPE_A);

    assertThat(result1).isPresent();
    assertThat(result1.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleA")));

    var result2 = underTest.getOrComputePathTranslation(CONFIG_SCOPE_A);

    assertThat(result2).isPresent();
    assertThat(result2.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleA")));

    verify(clientFs, times(1)).getFiles(any());
    verify(serverApi.component(), times(1)).getAllFileKeys(any(), any());
  }

  @Test
  void shouldRecomputePathTranslationsAfterBindingChange() {
    mockServerFilePaths(SONAR_PROJECT_A, "moduleA/src/Foo.java");
    mockClientFilePaths(CONFIG_SCOPE_A, "src/Foo.java");

    var result1 = underTest.getOrComputePathTranslation(CONFIG_SCOPE_A);

    assertThat(result1).isPresent();
    assertThat(result1.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleA")));

    mockServerFilePaths(SONAR_PROJECT_A, "moduleB/src/Foo.java");

    underTest.onBindingChanged(new BindingConfigChangedEvent(CONFIG_SCOPE_A, null, null));

    var result2 = underTest.getOrComputePathTranslation(CONFIG_SCOPE_A);

    assertThat(result2).isPresent();
    assertThat(result2.get())
      .usingRecursiveComparison()
      .isEqualTo(new FilePathTranslation(Paths.get(""), Paths.get("moduleB")));
  }

  @Test
  void shouldLogAndIgnoreOtherErrors() throws InterruptedException {
    when(serverApi.component().getAllFileKeys(any(), any())).thenAnswer(invocation -> {
      throw new IllegalStateException();
    });
    mockClientFilePaths(CONFIG_SCOPE_A, "src/Foo.java");

    var result = underTest.getOrComputePathTranslation(CONFIG_SCOPE_A);

    assertThat(result).isEmpty();
    assertThat(logTester.logs()).contains("Error while getting server file paths for project 'sonarProjectA'");
  }

  private void mockClientFilePaths(String configScopeId, String... paths) {
    doReturn(Arrays.stream(paths).map(path -> new ClientFile(null, null, Paths.get(path), null, null, null)).collect(Collectors.toList()))
      .when(clientFs)
      .getFiles(configScopeId);
  }

  private void mockServerFilePaths(String sonarProjectKey, String... paths) {
    doReturn(Arrays.stream(paths).map(path -> SONAR_PROJECT_A + ":" + path).collect(Collectors.toList()))
      .when(componentApi)
      .getAllFileKeys(eq(sonarProjectKey), any());
  }

}
