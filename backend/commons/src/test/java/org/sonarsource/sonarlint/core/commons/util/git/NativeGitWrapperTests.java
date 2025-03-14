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

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class NativeGitWrapperTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private final NativeGitWrapper nativeGit = spy(new NativeGitWrapper());

  @Test
  void shouldReturnEmptyGitExecutable() throws IOException {
    doThrow(new RuntimeException()).when(nativeGit).getGitExecutable();

    assertThat(nativeGit.getNativeGitExecutable()).isNull();
  }

  @Test
  void shouldConsiderNativeGitNotAvailableOnException() throws IOException {
    doThrow(new RuntimeException()).when(nativeGit).getGitExecutable();

    assertThat(nativeGit.checkIfNativeGitEnabled(Path.of(""))).isFalse();
  }

  @Test
  void shouldConsiderNativeGitNotAvailableOnNull() throws IOException {
    doReturn(null).when(nativeGit).getGitExecutable();

    assertThat(nativeGit.checkIfNativeGitEnabled(Path.of(""))).isFalse();
  }

}
