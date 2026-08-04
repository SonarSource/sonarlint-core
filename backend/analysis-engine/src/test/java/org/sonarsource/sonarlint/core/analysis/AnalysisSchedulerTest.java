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
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInstancesLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  @Test
  void should_close_loaded_plugins_exactly_once_on_shutdown(@TempDir Path workDir) throws Exception {
    var loadedPlugins = mockLoadedPlugins();
    analysisScheduler = new AnalysisScheduler(configuration(workDir), loadedPlugins, logTester.getLogOutput());

    analysisScheduler.stop();
    analysisScheduler.stop();

    verify(loadedPlugins, times(1)).close();
  }

  @Test
  void should_close_each_set_of_loaded_plugins_exactly_once_across_reset_and_shutdown(@TempDir Path workDir) throws Exception {
    var initialPlugins = mockLoadedPlugins();
    var replacementPlugins = mockLoadedPlugins();
    var replacementStarted = new CountDownLatch(1);
    when(replacementPlugins.getAnalysisPluginInstancesByKeys()).thenAnswer(ignored -> {
      replacementStarted.countDown();
      return java.util.Map.of();
    });
    var configuration = configuration(workDir);
    analysisScheduler = new AnalysisScheduler(configuration, initialPlugins, logTester.getLogOutput());

    analysisScheduler.reset(() -> new SchedulerResetConfiguration(configuration, replacementPlugins));
    assertThat(replacementStarted.await(5, TimeUnit.SECONDS)).isTrue();
    analysisScheduler.stop();

    verify(initialPlugins, times(1)).close();
    verify(replacementPlugins, times(1)).close();
  }

  @Test
  void should_close_loaded_plugins_when_the_global_container_fails_to_start(@TempDir Path workDir) throws Exception {
    var loadedPlugins = mock(LoadedPlugins.class);
    when(loadedPlugins.getAnalysisPluginInstancesByKeys()).thenThrow(new IllegalStateException("start failure"));

    assertThatThrownBy(() -> new AnalysisScheduler(configuration(workDir), loadedPlugins, logTester.getLogOutput()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("start failure");

    verify(loadedPlugins).close();
  }

  @Test
  void should_close_loaded_plugins_when_scheduler_initialization_fails_before_container_creation(@TempDir Path workDir) throws Exception {
    var loadedPlugins = mockLoadedPlugins();
    var logger = SonarLintLogger.get();
    var previousLogOutput = logger.getTargetForCopy();
    try {
      logger.setTarget(null);

      assertThatThrownBy(() -> new AnalysisScheduler(configuration(workDir), loadedPlugins, logTester.getLogOutput()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No log output configured");
    } finally {
      logger.setTarget(previousLogOutput);
    }

    verify(loadedPlugins).close();
  }

  private static AnalysisScheduler newScheduler(Path workDir) {
    return newScheduler(workDir, command -> {
    });
  }

  private static AnalysisScheduler newScheduler(Path workDir, Consumer<Command> commandDequeuedHook) {
    var loadedPlugins = new LoadedPlugins(new HashMap<>(), mock(PluginInstancesLoader.class), Set.of(), Set.of());
    return new AnalysisScheduler(configuration(workDir), loadedPlugins, logTester.getLogOutput(), commandDequeuedHook);
  }

  private static AnalysisSchedulerConfiguration configuration(Path workDir) {
    return AnalysisSchedulerConfiguration.builder()
      .setClientPid(1234L)
      .setWorkDir(workDir)
      .build();
  }

  private static LoadedPlugins mockLoadedPlugins() {
    var loadedPlugins = mock(LoadedPlugins.class);
    when(loadedPlugins.getAnalysisPluginInstancesByKeys()).thenReturn(java.util.Map.of());
    return loadedPlugins;
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
