/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.util.FileUtils.RECURSIVE;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class NativeGitLocatorTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static NativeGitLocator underTest;
  @TempDir
  private Path projectDirPath;

  @BeforeEach
  void prepare() {
    underTest = spy(new NativeGitLocator());
  }

  @AfterEach
  void cleanup() throws IOException {
    FileUtils.delete(projectDirPath.toFile(), RECURSIVE);
  }

  @Test
  void shouldConsiderNativeGitNotAvailableOnNull() {
    doReturn(Optional.empty()).when(underTest).getGitExecutable();

    assertThat(underTest.getNativeGitExecutable()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("gitLocations")
  void should_return_first_git_location(TestData testData, Optional<String> expectedLocation) {
    var location = NativeGitLocator.locateGitOnWindows(testData.whereToolResult, testData.lines());

    AssertionsForClassTypes.assertThat(location).isEqualTo(expectedLocation);
  }

  private static Stream<Arguments> gitLocations() {
    return Stream.of(
      Arguments.of(result(0), Optional.empty()),
      Arguments.of(result(1, "invalid location"), Optional.empty()),
      Arguments.of(result(0, "invalid location"), Optional.empty()),
      Arguments.of(result(0, "C:\\Program Files\\Git\\bin\\git.exe"), Optional.of("C:\\Program Files\\Git\\bin\\git.exe")),
      // Multiple Git installations on the PATH: where.exe returns one path per line and we must pick the first one (SLCORE / USER-2264).
      Arguments.of(result(0, "C:\\Users\\user.name\\AppData\\Local\\Programs\\Git\\cmd\\git.exe",
        "C:\\Users\\user.name\\AppData\\Local\\Programs\\Git\\mingw64\\bin\\git.exe"), Optional.of("C:\\Users\\user.name\\AppData\\Local\\Programs\\Git\\cmd\\git.exe")),
      Arguments.of(result(0, "C:\\Program Files\\Git\\cmd\\git.exe", "C:\\Program Files\\Git2ndrun\\bin\\git.exe"),
        Optional.of("C:\\Program Files\\Git\\cmd\\git.exe")));
  }

  private static TestData result(int code, String... lines) {
    return new TestData(new ProcessWrapperFactory.ProcessExecutionResult(code), List.of(lines));
  }

  private record TestData(ProcessWrapperFactory.ProcessExecutionResult whereToolResult, List<String> lines) {
  }
}
