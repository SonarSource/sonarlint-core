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

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PathTranslationServiceTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private PathTranslationService underTest;
  private ConfigurationRepository configurationRepository;
  private AsyncLoadingCache<String, FilePathTranslation> cachedPathsTranslationByConfigScope;

  @BeforeEach
  void prepare() {
    configurationRepository = mock(ConfigurationRepository.class);
    cachedPathsTranslationByConfigScope = mock(AsyncLoadingCache.class);
    underTest = new PathTranslationService(mock(ClientFileSystemService.class), mock(ServerApiProvider.class), configurationRepository);

  }

  @Test
  void shouldRethrowOnExecutionException() {
    when(cachedPathsTranslationByConfigScope.get(anyString())).thenThrow(new CancellationException());

    assertThrows(IllegalStateException.class, () -> underTest
      .getOrComputePathTranslation("scope", cachedPathsTranslationByConfigScope, 0));
    verify(cachedPathsTranslationByConfigScope, times(5)).get(anyString());
  }

  @Test
  void shouldThrowWhenCounterHigherThanLimit() {
    when(cachedPathsTranslationByConfigScope.get(anyString())).thenThrow(new CancellationException());

    assertThrows(IllegalStateException.class, () -> underTest
      .getOrComputePathTranslation("scope", cachedPathsTranslationByConfigScope, 4));
    verify(cachedPathsTranslationByConfigScope, times(1)).get(anyString());
  }

}
