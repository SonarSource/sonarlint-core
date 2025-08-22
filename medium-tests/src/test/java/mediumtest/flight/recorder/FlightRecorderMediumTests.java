/*
 * SonarLint Core - Medium Tests
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
package mediumtest.flight.recorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.commons.testutils.GitUtils;
import org.sonarsource.sonarlint.core.flight.recorder.FlightRecorderStorageService;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.flight.recorder.FlightRecorderService.SONARLINT_FLIGHT_RECORDER_PERIOD_PROPERTY;

class FlightRecorderMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private final Path logFolder = SonarLintUserHome.get().resolve("log");

  @AfterEach
  void tearDown() throws IOException{
    FileUtils.delete(logFolder.toFile(), FileUtils.RECURSIVE);
    System.clearProperty(SONARLINT_FLIGHT_RECORDER_PERIOD_PROPERTY);
  }

  @SonarLintTest
  void test_git_data_added_to_file(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException, GitAPIException {
    var gitRepo = tmp.resolve("git-repo");
    Files.createDirectory(gitRepo);

    try (var git = GitUtils.createRepository(gitRepo)) {
      GitUtils.createFile(gitRepo, "file1");
      GitUtils.commit(git, "file1");
      GitUtils.createFile(gitRepo, "file2");
      GitUtils.commit(git, "file2");
      GitUtils.createFile(gitRepo, "file3");
      GitUtils.commit(git, "file3");
    }

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, gitRepo, List.of())
      .build();

    harness.newBackend()
      .start(fakeClient);

    var file = getFlightRecorderFile();
    assertThat(file)
      .isPresent()
      .get(InstanceOfAssertFactories.PATH)
      .content()
      .contains("git.history.length=3", "git.version=");
  }

  @SonarLintTest
  void test_create_flight_recorder_folder_if_it_does_not_exist(SonarLintTestHarness harness) {
    harness.newBackend()
      .start();

    assertThat(logFolder).exists();
  }

  @SonarLintTest
  void test_create_flight_recorder_file(SonarLintTestHarness harness) {
    harness.newBackend()
      .start();

    assertThat(logFolder).isDirectoryContaining("glob:**flight-recording-session-*");
  }

  @SonarLintTest
  void test_scheduled_updates(SonarLintTestHarness harness) {
    System.setProperty(SONARLINT_FLIGHT_RECORDER_PERIOD_PROPERTY, "1");

    harness.newBackend()
      .start();

    assertThat(logFolder).isDirectoryContaining("glob:**flight-recording-session-*");
    await().atMost(3, TimeUnit.SECONDS)
        .untilAsserted(this::fileContainsMultipleUpdates);

  }

  private void fileContainsMultipleUpdates() throws IOException {
    var flightRecorderFile = getFlightRecorderFile();
    assertThat(flightRecorderFile)
      .isPresent()
      .get(InstanceOfAssertFactories.PATH)
      .content()
      .containsSubsequence("param=1", "param=1");
  }

  @NotNull
  private Optional<Path> getFlightRecorderFile() throws IOException {
    try (var files = Files.list(logFolder)) {
      return files
          .filter(path -> path.getFileName().toString().startsWith("flight-recording-session-"))
          .map(path -> path.resolve(FlightRecorderStorageService.FILE_NAME))
          .findFirst();
    }
  }
}
