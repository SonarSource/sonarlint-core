/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.sonarsource.sonarlint.core.commons.SonarUserHome;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.telemetry.common.TelemetryUserSetting;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(SystemStubsExtension.class)
class MachineIdProviderTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @SystemStub
  EnvironmentVariables environmentVariables;

  @Test
  void should_return_null_when_telemetry_is_disabled(@TempDir Path tempDir) {
    environmentVariables.set(SonarUserHome.SONAR_USER_HOME_ENV, tempDir.toString());
    var userSetting = telemetryEnabled(false);

    var machineId = new MachineIdProvider(userSetting).getMachineId();

    assertThat(machineId).isNull();
    assertThat(tempDir.resolve("user")).doesNotExist();
  }

  @Test
  void should_create_and_persist_a_new_machine_id_when_file_is_absent(@TempDir Path tempDir) {
    environmentVariables.set(SonarUserHome.SONAR_USER_HOME_ENV, tempDir.toString());
    var userSetting = telemetryEnabled(true);

    var machineId = new MachineIdProvider(userSetting).getMachineId();

    assertThat(machineId).isNotBlank();
    assertThat(UUID.fromString(machineId)).isNotNull();
    assertThat(tempDir.resolve("user")).exists().hasContent(machineId);
  }

  @Test
  void should_read_an_existing_machine_id_verbatim(@TempDir Path tempDir) throws IOException {
    environmentVariables.set(SonarUserHome.SONAR_USER_HOME_ENV, tempDir.toString());
    var cliWrittenId = "97323cbb-914c-49e2-b603-9260861dbb7b";
    Files.writeString(tempDir.resolve("user"), cliWrittenId);
    var userSetting = telemetryEnabled(true);

    var machineId = new MachineIdProvider(userSetting).getMachineId();

    assertThat(machineId).isEqualTo(cliWrittenId);
  }

  @Test
  void should_recreate_a_blank_file(@TempDir Path tempDir) throws IOException {
    environmentVariables.set(SonarUserHome.SONAR_USER_HOME_ENV, tempDir.toString());
    Files.writeString(tempDir.resolve("user"), "  ");
    var userSetting = telemetryEnabled(true);

    var machineId = new MachineIdProvider(userSetting).getMachineId();

    assertThat(machineId).isNotBlank();
    assertThat(tempDir.resolve("user")).hasContent(machineId);
  }

  @Test
  void should_read_the_winner_id_when_every_create_attempt_loses_the_race(@TempDir Path tempDir) {
    environmentVariables.set(SonarUserHome.SONAR_USER_HOME_ENV, tempDir.toString());
    var userFile = tempDir.resolve("user");
    var winnerId = UUID.randomUUID().toString();
    var userSetting = telemetryEnabled(true);

    try (var filesMock = mockStatic(Files.class, Answers.CALLS_REAL_METHODS)) {
      filesMock.when(() -> Files.writeString(eq(userFile), any(), eq(StandardOpenOption.CREATE_NEW)))
        .thenThrow(new FileAlreadyExistsException(userFile.toString()));
      filesMock.when(() -> Files.deleteIfExists(userFile))
        .thenReturn(false)
        .thenAnswer(invocation -> {
          // Simulate the winner's write becoming visible on disk right after our last losing attempt.
          Files.writeString(userFile, winnerId);
          return false;
        });

      var machineId = new MachineIdProvider(userSetting).getMachineId();

      assertThat(machineId).isEqualTo(winnerId);
    }
  }

  @Test
  void should_return_null_when_the_shared_home_cannot_be_created(@TempDir Path tempDir) {
    var unwritableParent = tempDir.resolve("not-a-directory");
    unwritableParent.toFile().getParentFile().mkdirs();
    try {
      Files.writeString(unwritableParent, "blocking file");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    environmentVariables.set(SonarUserHome.SONAR_USER_HOME_ENV, unwritableParent.resolve("sonar").toString());
    var userSetting = telemetryEnabled(true);

    var machineId = new MachineIdProvider(userSetting).getMachineId();

    assertThat(machineId).isNull();
  }

  @Test
  void should_cache_the_resolved_value_for_the_provider_lifetime(@TempDir Path tempDir) {
    environmentVariables.set(SonarUserHome.SONAR_USER_HOME_ENV, tempDir.toString());
    var userSetting = telemetryEnabled(true);
    var provider = new MachineIdProvider(userSetting);

    var first = provider.getMachineId();
    try {
      Files.writeString(tempDir.resolve("user"), UUID.randomUUID().toString());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    var second = provider.getMachineId();

    assertThat(second).isEqualTo(first);
  }

  @Test
  void concurrent_creation_should_converge_on_the_same_value(@TempDir Path tempDir) throws InterruptedException {
    environmentVariables.set(SonarUserHome.SONAR_USER_HOME_ENV, tempDir.toString());
    var userSetting = telemetryEnabled(true);
    var threadCount = 30;
    var results = new String[threadCount];
    var ready = new CountDownLatch(threadCount);
    var start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    for (var i = 0; i < results.length; i++) {
      var index = i;
      pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        results[index] = new MachineIdProvider(userSetting).getMachineId();
      });
    }
    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    var expected = results[0];
    assertThat(expected).isNotBlank();
    assertThat(results).containsOnly(expected);
    assertThat(tempDir.resolve("user")).hasContent(expected);
  }

  private static TelemetryUserSetting telemetryEnabled(boolean enabled) {
    var userSetting = mock(TelemetryUserSetting.class);
    when(userSetting.isTelemetryEnabledByUser()).thenReturn(enabled);
    return userSetting;
  }
}
