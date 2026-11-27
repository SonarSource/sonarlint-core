/*
 * SonarLint Core - RPC Integration Tests
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
package integrationtests.storage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static integrationtests.storage.StorageConcurrentStartupIntegrationTests.copyOfLatestSnapshot;
import static integrationtests.storage.StorageConcurrentStartupIntegrationTests.expectedServerFindings;
import static integrationtests.storage.StorageConcurrentStartupIntegrationTests.javaExecutable;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first backend to open the storage serves it to the others. When its IDE window is closed, the others must replace it without failing the requests they serve.
 */
class StorageOwnerLeavesIntegrationTests {

  // Backends disturb each other when three or more replace the owner at the same time
  private static final int REMAINING_PROCESSES = 4;

  private final List<Process> processes = new ArrayList<>();
  private final ExecutorService outputReaders = Executors.newCachedThreadPool();

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
  void should_keep_using_the_storage_when_the_ide_window_owning_it_is_closed(@TempDir Path tempDir) throws Exception {
    var storageRoot = copyOfLatestSnapshot(tempDir);
    var leaveFile = tempDir.resolve("leave");
    var useFile = tempDir.resolve("use");
    var stopFile = tempDir.resolve("stop");
    var owner = start(ShareStorageProcess.OWNER, storageRoot, leaveFile, stopFile);
    assertThat(owner.nextOutcome()).isEqualTo(ShareStorageProcess.OPENED);
    var remaining = new ArrayList<OutputLines>();
    for (var i = 0; i < REMAINING_PROCESSES; i++) {
      remaining.add(start(ShareStorageProcess.USER, storageRoot, useFile, stopFile));
    }
    for (var process : remaining) {
      assertThat(process.nextOutcome()).isEqualTo(ShareStorageProcess.OPENED);
    }

    Files.createFile(leaveFile);
    assertThat(owner.process.waitFor(1, TimeUnit.MINUTES)).as("The owner exits").isTrue();
    // Release all the remaining processes at the same moment, so that they replace the owner together
    Files.createFile(useFile);
    var results = new ArrayList<String>();
    for (var process : remaining) {
      results.add(process.nextOutcome());
    }
    Files.createFile(stopFile);

    assertThat(results).containsOnly(ShareStorageProcess.USED + expectedServerFindings());
  }

  private OutputLines start(String role, Path storageRoot, Path goFile, Path stopFile) throws Exception {
    var process = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"), ShareStorageProcess.class.getName(),
      role, storageRoot.toString(), goFile.toString(), stopFile.toString())
      .redirectErrorStream(true)
      .start();
    processes.add(process);
    var output = new OutputLines(process);
    outputReaders.execute(output::read);
    return output;
  }

  private static final class OutputLines {
    private static final String EXITED = "EXITED";
    private final Process process;
    private final BlockingQueue<String> outcomes = new LinkedBlockingQueue<>();

    private OutputLines(Process process) {
      this.process = process;
    }

    private void read() {
      try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.equals(ShareStorageProcess.OPENED) || line.startsWith(ShareStorageProcess.USED) || line.startsWith(ShareStorageProcess.FAILED)) {
            outcomes.add(line);
          } else {
            System.out.println("[process " + process.pid() + "] " + line);
          }
        }
      } catch (Exception e) {
        outcomes.add("ERROR " + e);
      }
      outcomes.add(EXITED);
    }

    private String nextOutcome() throws InterruptedException {
      var outcome = outcomes.poll(2, TimeUnit.MINUTES);
      return outcome == null ? "TIMED OUT" : outcome;
    }
  }
}
