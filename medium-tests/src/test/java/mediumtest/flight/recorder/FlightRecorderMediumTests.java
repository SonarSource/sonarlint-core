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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.sonarsource.sonarlint.core.flight.recorder.FlightRecorderStorageService;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.flight.recorder.FlightRecorderService.SONARLINT_FLIGHT_RECORDER_PERIOD_PROPERTY;

class FlightRecorderMediumTests {

  private Path logFolder;

  @AfterEach
  void tearDown() throws IOException {
    FileUtils.delete(logFolder.toFile(), FileUtils.RECURSIVE);
    System.clearProperty(SONARLINT_FLIGHT_RECORDER_PERIOD_PROPERTY);
  }

  @SonarLintTest
  void test_create_flight_recorder_folder_if_it_does_not_exist(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();
    logFolder = logFolder(backend);

    assertThat(logFolder).exists();
  }

  @SonarLintTest
  void test_create_flight_recorder_file(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();
    logFolder = logFolder(backend);

    assertThat(logFolder).isDirectoryContaining("glob:**flight-recording-session-*");
  }

  @SonarLintTest
  void test_scheduled_updates(SonarLintTestHarness harness) {
    System.setProperty(SONARLINT_FLIGHT_RECORDER_PERIOD_PROPERTY, "1");

    var backend = harness.newBackend()
      .start();
    logFolder = logFolder(backend);

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

  private Optional<Path> getFlightRecorderFile() throws IOException {
    try (var files = Files.list(logFolder)) {
      return files
          .filter(path -> path.getFileName().toString().startsWith("flight-recording-session-"))
          .map(path -> path.resolve(FlightRecorderStorageService.FILE_NAME))
          .findFirst();
    }
  }

  private static Path logFolder(SonarLintTestRpcServer backend) {
    return backend.getUserHome().resolve("log");
  }
}
