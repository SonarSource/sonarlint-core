/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.command.Command;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInstancesLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalysisSchedulerTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester(true);

  private AnalysisScheduler analysisScheduler;

  @AfterEach
  void stopScheduler() {
    if (analysisScheduler != null) {
      analysisScheduler.stop();
    }
  }

  @Test
  void should_cancel_command_taken_from_queue_when_stopping_before_it_starts_executing(@TempDir Path workDir) throws Exception {
    var commandDequeued = new CountDownLatch(1);
    var waitUntilInterrupted = new CountDownLatch(1);
    analysisScheduler = newScheduler(workDir, command -> {
      commandDequeued.countDown();
      try {
        waitUntilInterrupted.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    var command = new TestCommand();

    analysisScheduler.post(command);
    assertThat(commandDequeued.await(5, TimeUnit.SECONDS)).isTrue();

    analysisScheduler.stop();

    assertThat(command.wasExecuted()).isFalse();
    assertThat(command.cancelCount()).isEqualTo(1);
  }

  @Test
  void should_return_early_when_stop_is_called_while_already_terminating(@TempDir Path workDir) throws Exception {
    analysisScheduler = newScheduler(workDir);
    var allowCommandToFinish = new CountDownLatch(1);
    var command = new BlockingCommand(allowCommandToFinish);

    analysisScheduler.post(command);
    assertThat(command.executionStarted().await(5, TimeUnit.SECONDS)).isTrue();

    var firstStop = CompletableFuture.runAsync(analysisScheduler::stop);
    assertThat(command.interrupted().await(5, TimeUnit.SECONDS)).isTrue();

    try {
      var secondStop = CompletableFuture.runAsync(analysisScheduler::stop);
      secondStop.get(1, TimeUnit.SECONDS);

      assertThat(firstStop.isDone()).isFalse();
      assertThat(command.cancelCount()).isEqualTo(1);
    } finally {
      allowCommandToFinish.countDown();
      firstStop.get(5, TimeUnit.SECONDS);
    }
  }

  private static AnalysisScheduler newScheduler(Path workDir) {
    return newScheduler(workDir, command -> {
    });
  }

  private static AnalysisScheduler newScheduler(Path workDir, Consumer<Command> commandDequeuedHook) {
    var analysisGlobalConfig = AnalysisSchedulerConfiguration.builder()
      .setClientPid(1234L)
      .setWorkDir(workDir)
      .build();
    var loadedPlugins = new LoadedPlugins(new HashMap<>(), mock(PluginInstancesLoader.class), Set.of(), Set.of());
    return new AnalysisScheduler(analysisGlobalConfig, loadedPlugins, logTester.getLogOutput(), commandDequeuedHook);
  }

  private static class TestCommand extends Command {
    private final AtomicBoolean executed = new AtomicBoolean();
    private final AtomicInteger cancelCount = new AtomicInteger();

    @Override
    public void execute(ModuleRegistry moduleRegistry) {
      executed.set(true);
    }

    @Override
    public void cancel() {
      cancelCount.incrementAndGet();
    }

    boolean wasExecuted() {
      return executed.get();
    }

    int cancelCount() {
      return cancelCount.get();
    }
  }

  private static class BlockingCommand extends Command {
    private final CountDownLatch executionStarted = new CountDownLatch(1);
    private final CountDownLatch interrupted = new CountDownLatch(1);
    private final CountDownLatch allowCommandToFinish;
    private final AtomicInteger cancelCount = new AtomicInteger();

    private BlockingCommand(CountDownLatch allowCommandToFinish) {
      this.allowCommandToFinish = allowCommandToFinish;
    }

    @Override
    public void execute(ModuleRegistry moduleRegistry) {
      executionStarted.countDown();
      try {
        Thread.sleep(30_000);
      } catch (InterruptedException e) {
        interrupted.countDown();
      }
      try {
        allowCommandToFinish.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public void cancel() {
      cancelCount.incrementAndGet();
    }

    CountDownLatch executionStarted() {
      return executionStarted;
    }

    CountDownLatch interrupted() {
      return interrupted;
    }

    int cancelCount() {
      return cancelCount.get();
    }
  }
}
