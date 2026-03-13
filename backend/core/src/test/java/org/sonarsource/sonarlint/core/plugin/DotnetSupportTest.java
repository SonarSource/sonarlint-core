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
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class DotnetSupportTest {
  private static final Path somePath = Paths.get("folder", "file.txt");

  static Stream<Arguments> provideTestArguments() {
    return Stream.of(
      Arguments.of(null, true, true, true, false),
      Arguments.of(null, true, true, false, true),
      Arguments.of(somePath, true, false, true, false),
      Arguments.of(somePath, true, false, false, true),
      Arguments.of(somePath, false, true, true, false),
      Arguments.of(somePath, false, true, false, true),
      Arguments.of(somePath, false, false, true, false),
      Arguments.of(somePath, false, false, false, true),
      Arguments.of(somePath, false, false, false, false)
    );
  }

  @ParameterizedTest
  @MethodSource("provideTestArguments")
  void should_initialize_properties_as_expected(@Nullable Path csharpAnalyzerPath, boolean shouldUseCsharpEnterprise, boolean shouldUseVbNetEnterprise,
    boolean supportsCsharp, boolean supportsVbNet) {
    var underTest = new DotnetSupport(csharpAnalyzerPath, shouldUseCsharpEnterprise, shouldUseVbNetEnterprise, supportsCsharp, supportsVbNet, Map.of());

    assertThat(underTest.isSupportsCsharp()).isEqualTo(supportsCsharp);
    assertThat(underTest.isSupportsVbNet()).isEqualTo(supportsVbNet);
    assertThat(underTest.getActualCsharpAnalyzerPath()).isEqualTo(csharpAnalyzerPath);
    assertThat(underTest.isShouldUseCsharpEnterprise()).isEqualTo(shouldUseCsharpEnterprise);
    assertThat(underTest.isShouldUseVbNetEnterprise()).isEqualTo(shouldUseVbNetEnterprise);
  }
}
