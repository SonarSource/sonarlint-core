/*
 * SonarLint Core - Medium Tests
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
package mediumtest.storage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_FINDINGS;

/**
 * Each IDE window runs its own backend, and they all share the same storage. Backends of the same process share the database, so the processes must be separate to
 * compete for it.
 */
class StorageConcurrentStartupMediumTests {

  // Some processes failed every time with 5 before backends took turns to open the database
  private static final int PROCESSES = 5;

  private final List<Process> processes = new ArrayList<>();
  // One reader per process, as each one blocks until all the processes are ready
  private final ExecutorService outputReaders = Executors.newFixedThreadPool(PROCESSES);

  @AfterEach
  void stopProcesses() throws InterruptedException {
    processes.forEach(Process::destroyForcibly);
    // On Windows, the storage cannot be deleted until the processes holding it have exited
    for (var process : processes) {
      process.waitFor(30, TimeUnit.SECONDS);
    }
    outputReaders.shutdownNow();
  }

  @Test
  void should_open_the_storage_when_several_ide_windows_start_together(@TempDir Path tempDir) throws Exception {
    var storageRoot = copyOfLatestSnapshot(tempDir);
    var startFile = tempDir.resolve("start");
    var stopFile = tempDir.resolve("stop");
    var readiness = new ArrayList<CompletableFuture<Void>>();
    var outcomes = new ArrayList<CompletableFuture<String>>();
    for (var i = 0; i < PROCESSES; i++) {
      var process = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"), OpenStorageProcess.class.getName(),
        storageRoot.toString(), startFile.toString(), stopFile.toString())
        .redirectErrorStream(true)
        .start();
      processes.add(process);
      var ready = new CompletableFuture<Void>();
      readiness.add(ready);
      outcomes.add(CompletableFuture.supplyAsync(() -> outcome(process, ready), outputReaders));
    }

    // Release all the processes at the same moment, once their JVMs are up
    CompletableFuture.allOf(readiness.toArray(CompletableFuture[]::new)).orTimeout(2, TimeUnit.MINUTES).join();
    Files.createFile(startFile);
    var results = outcomes.stream().map(outcome -> outcome.orTimeout(2, TimeUnit.MINUTES).join()).toList();
    Files.createFile(stopFile);

    assertThat(results).containsOnly(OpenStorageProcess.OPENED + expectedServerFindings());
  }

  @Test
  void should_wait_for_another_ide_window_to_open_the_storage(@TempDir Path tempDir) throws Exception {
    var storageRoot = copyOfLatestSnapshot(tempDir);
    var stopFile = tempDir.resolve("stop");
    holdStartupTurn(storageRoot, stopFile);

    var opening = CompletableFuture.supplyAsync(() -> new SonarLintDatabase(storageRoot), outputReaders);

    await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).until(() -> !opening.isDone());
    Files.createFile(stopFile);
    var database = opening.get(1, TimeUnit.MINUTES);
    try {
      assertThat(database.dsl().fetchCount(SERVER_FINDINGS)).isEqualTo(expectedServerFindings());
    } finally {
      database.shutdown();
    }
  }

  @Test
  void should_stop_waiting_for_another_ide_window_when_the_startup_is_cancelled(@TempDir Path tempDir) throws Exception {
    var storageRoot = copyOfLatestSnapshot(tempDir);
    holdStartupTurn(storageRoot, tempDir.resolve("stop"));
    var failure = new CompletableFuture<Throwable>();
    var interruptedAfterFailure = new CompletableFuture<Boolean>();
    var startup = new Thread(() -> {
      try {
        new SonarLintDatabase(storageRoot).shutdown();
        failure.complete(null);
      } catch (Exception e) {
        interruptedAfterFailure.complete(Thread.currentThread().isInterrupted());
        failure.complete(e);
      }
    });
    startup.start();

    // Waiting for its turn
    await().until(() -> startup.getState() == Thread.State.TIMED_WAITING);
    startup.interrupt();

    assertThat(failure.get(1, TimeUnit.MINUTES)).hasRootCauseInstanceOf(InterruptedException.class);
    assertThat(interruptedAfterFailure.get()).isTrue();
  }

  @Test
  void should_open_the_storage_when_ide_windows_cannot_take_turns(@TempDir Path tempDir) throws Exception {
    var storageRoot = copyOfLatestSnapshot(tempDir);
    // Cannot be opened as a file
    Files.createDirectories(storageRoot.resolve("h2").resolve(SonarLintDatabase.STARTUP_LOCK_FILENAME));

    var database = new SonarLintDatabase(storageRoot);

    try {
      assertThat(database.dsl().fetchCount(SERVER_FINDINGS)).isEqualTo(expectedServerFindings());
    } finally {
      database.shutdown();
    }
  }

  /**
   * Like another IDE window still opening the storage, until the stop file exists
   */
  private void holdStartupTurn(Path storageRoot, Path stopFile) throws Exception {
    var process = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"), HoldStartupTurnProcess.class.getName(),
      storageRoot.toString(), stopFile.toString())
      .redirectErrorStream(true)
      .start();
    processes.add(process);
    var holding = CompletableFuture.supplyAsync(() -> {
      try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.equals(HoldStartupTurnProcess.HOLDING)) {
            return true;
          }
        }
        return false;
      } catch (Exception e) {
        return false;
      }
    }, outputReaders);
    assertThat(holding.get(2, TimeUnit.MINUTES)).as("The other IDE window takes the turn").isTrue();
  }

  private static Path copyOfLatestSnapshot(Path tempDir) throws Exception {
    var storageRoot = tempDir.resolve("storage");
    FileUtils.copyDirectory(StorageSnapshot.latestSnapshotDirectory().resolve("h2").toFile(), storageRoot.resolve("h2").toFile());
    return storageRoot;
  }

  private static int expectedServerFindings() throws Exception {
    return StorageSnapshot.Metadata.read(StorageSnapshot.latestSnapshotDirectory()).rowsByTable().get("SERVER_FINDINGS");
  }

  private static String outcome(Process process, CompletableFuture<Void> ready) {
    try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.equals(OpenStorageProcess.READY)) {
          ready.complete(null);
        } else if (line.startsWith(OpenStorageProcess.OPENED) || line.startsWith(OpenStorageProcess.FAILED)) {
          return line;
        }
      }
      return "EXITED with " + process.waitFor();
    } catch (Exception e) {
      return "ERROR " + e;
    } finally {
      // Do not wait forever for a process that died before being ready
      ready.complete(null);
    }
  }

  private static String javaExecutable() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }
}
