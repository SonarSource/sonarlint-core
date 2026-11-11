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

import static org.assertj.core.api.Assertions.assertThat;

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
    var storageRoot = tempDir.resolve("storage");
    FileUtils.copyDirectory(StorageSnapshot.latestSnapshotDirectory().resolve("h2").toFile(), storageRoot.resolve("h2").toFile());
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

    var expectedFindings = StorageSnapshot.Metadata.read(StorageSnapshot.latestSnapshotDirectory()).rowsByTable().get("SERVER_FINDINGS");
    assertThat(results).containsOnly(OpenStorageProcess.OPENED + expectedFindings);
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
