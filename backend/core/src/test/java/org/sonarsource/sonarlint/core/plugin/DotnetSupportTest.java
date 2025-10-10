/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DotnetSupportTest {
  private static final Path somePath = Paths.get("folder", "file.txt");
  private InitializeParams initializeParams;

  static Stream<Arguments> provideTestArguments() {
    return Stream.of(
      Arguments.of(Language.CS, null, true, true),
      Arguments.of(Language.VBNET, null, true, true),
      Arguments.of(Language.CS, somePath, true, false),
      Arguments.of(Language.VBNET, somePath, true, false),
      Arguments.of(Language.CS, somePath, false, true),
      Arguments.of(Language.VBNET, somePath, false, true),
      Arguments.of(Language.CS, somePath, false, false),
      Arguments.of(Language.VBNET, somePath, false, false),
      Arguments.of(Language.COBOL, somePath, false, false)
    );
  }

  @BeforeEach
  void prepare() {
    initializeParams = mock(InitializeParams.class);
  }

  @ParameterizedTest
  @MethodSource("provideTestArguments")
  void should_initialize_properties_as_expected(Language language, @Nullable Path csharpAnalyzerPath, boolean shouldUseCsharpEnterprise, boolean shouldUseVbNetEnterprise) {
    mockEnabledLanguages(language);

    var underTest = new DotnetSupport(initializeParams, csharpAnalyzerPath, shouldUseCsharpEnterprise, shouldUseVbNetEnterprise);

    assertThat(underTest.isSupportsCsharp()).isEqualTo(language == Language.CS);
    assertThat(underTest.isSupportsVbNet()).isEqualTo(language == Language.VBNET);
    assertThat(underTest.getActualCsharpAnalyzerPath()).isEqualTo(csharpAnalyzerPath);
    assertThat(underTest.isShouldUseCsharpEnterprise()).isEqualTo(shouldUseCsharpEnterprise);
    assertThat(underTest.isShouldUseVbNetEnterprise()).isEqualTo(shouldUseVbNetEnterprise);
  }

  private void mockEnabledLanguages(Language... languages) {
    when(initializeParams.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of(languages));
  }
}
