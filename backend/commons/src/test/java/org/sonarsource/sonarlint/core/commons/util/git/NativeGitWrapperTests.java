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
