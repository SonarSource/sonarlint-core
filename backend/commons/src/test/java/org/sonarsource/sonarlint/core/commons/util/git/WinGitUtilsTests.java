/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

class WinGitUtilsTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @EnabledOnOs(WINDOWS)
  @ParameterizedTest
  @MethodSource("gitLocations")
  void should_return_first_git_location(TestData testData, Optional<String> expectedLocation) {
    var location = WinGitUtils.locateGitOnWindows(testData.whereToolResult, testData.lines());

    assertThat(location).isEqualTo(expectedLocation);
  }

  private static Stream<Arguments> gitLocations() {
    return Stream.of(
      Arguments.of(result(0, ""), Optional.empty()),
      Arguments.of(result(1, "invalid location"), Optional.empty()),
      Arguments.of(result(0, "C:\\Program Files\\Git\\bin\\git.exe"), Optional.of("C:\\Program Files\\Git\\bin\\git.exe") ),
      Arguments.of(result(0, "C:\\Users\\user.name\\AppData\\Local\\Programs\\Git\\cmd\\git.exe" + System.lineSeparator() +
        "C:\\Users\\user.name\\AppData\\Local\\Programs\\Git\\mingw64\\bin\\git.exe"), Optional.of("C:\\Users\\user.name\\AppData\\Local\\Programs\\Git\\cmd\\git.exe") )
    );
  }

  private static TestData result(int code, String output) {
    return new TestData(new ProcessWrapperFactory.ProcessExecutionResult(code), output);
  }

  private record TestData(ProcessWrapperFactory.ProcessExecutionResult whereToolResult, String lines) {
  }
}
