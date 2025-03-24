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
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class ProcessWrapperFactoryTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void it_should_execute_git(@TempDir Path baseDir) {
    assumeTrue(new NativeGitWrapper().getNativeGitExecutable().isPresent());
    var result = new ProcessWrapperFactory().create(baseDir, "git", "--version").execute();

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("git version ");
  }

  @Test
  void it_should_return_output_for_invalid_command(@TempDir Path baseDir) {
    assumeTrue(new NativeGitWrapper().getNativeGitExecutable().isPresent());
    var processWrapper = new ProcessWrapperFactory().create(baseDir, "git", "-version");
    var result = processWrapper.execute();
    assertThat(result.exitCode()).isEqualTo(129);
    assertThat(result.output()).contains("unknown option: -version");
  }

  @Test
  void it_should_gracefully_return_output_for_interrupted_exception(@TempDir Path baseDir) throws InterruptedException {
    assumeTrue(new NativeGitWrapper().getNativeGitExecutable().isPresent());
    var processWrapper = new ProcessWrapperFactory().create(baseDir, "git", "--version");
    var spy = spy(processWrapper);
    doThrow(InterruptedException.class).when(spy).runProcessAndGetOutput(any(), any());
    var result = spy.execute();

    assertThat(result.exitCode()).isEqualTo(-1);
    assertThat(result.output()).contains("");
  }

  @Test
  void it_should_gracefully_return_output_for_exception(@TempDir Path baseDir) throws InterruptedException {
    assumeTrue(new NativeGitWrapper().getNativeGitExecutable().isPresent());
    var processWrapper = new ProcessWrapperFactory().create(baseDir, "git", "--version");
    var spy = spy(processWrapper);
    doThrow(RuntimeException.class).when(spy).runProcessAndGetOutput(any(), any());
    var result = spy.execute();

    assertThat(result.exitCode()).isEqualTo(-1);
    assertThat(result.output()).contains("");
  }

  @Test
  void it_should_gracefully_return_output_when_not_able_to_create_process(@TempDir Path baseDir) throws IOException {
    var processWrapper = new ProcessWrapperFactory().create(baseDir, "git", "--version");
    var spy = spy(processWrapper);
    doThrow(IOException.class).when(spy).createProcess();
    var result = spy.execute();

    assertThat(result.exitCode()).isEqualTo(-2);
    assertThat(result.output()).contains("");
  }
}
