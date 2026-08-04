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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.command.Command;
import org.sonarsource.sonarlint.core.analysis.command.ResetPluginsCommand;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;

public class AnalysisScheduler {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final AtomicReference<GlobalAnalysisContainer> globalAnalysisContainer = new AtomicReference<>();
  private final AnalysisQueue analysisQueue;
  private final Thread analysisThread;
  private final LogOutput logOutput;
  private final Consumer<Command> commandDequeuedHook;
  private final AtomicReference<Runnable> termination = new AtomicReference<>();
  private final AtomicReference<Command> executingCommand = new AtomicReference<>();

  public AnalysisScheduler(AnalysisSchedulerConfiguration analysisGlobalConfig, LoadedPlugins loadedPlugins, @Nullable LogOutput logOutput) {
    this(analysisGlobalConfig, loadedPlugins, logOutput, command -> {
    });
  }

  // Package-private for tests that need to control the queue-to-execution handoff.
  AnalysisScheduler(AnalysisSchedulerConfiguration analysisGlobalConfig, LoadedPlugins loadedPlugins, @Nullable LogOutput logOutput, Consumer<Command> commandDequeuedHook) {
    this.logOutput = logOutput;
    this.commandDequeuedHook = commandDequeuedHook;
    try {
      this.analysisQueue = new AnalysisQueue();
      this.analysisThread = new Thread(this::executeQueuedCommands, "sonarlint-analysis-scheduler");
    } catch (RuntimeException | Error initializationFailure) {
      closePluginsAfterInitializationFailure(loadedPlugins, initializationFailure);
      throw initializationFailure;
    }
    // if the container cannot be started, the thread won't be started
    var analysisContainer = new GlobalAnalysisContainer(analysisGlobalConfig, loadedPlugins);
    analysisContainer.startComponents();
    globalAnalysisContainer.set(analysisContainer);
    try {
      analysisThread.start();
    } catch (RuntimeException | Error startFailure) {
      try {
        analysisContainer.stopComponents();
      } catch (RuntimeException | Error stopFailure) {
        startFailure.addSuppressed(stopFailure);
      }
      throw startFailure;
    }
  }

  private static void closePluginsAfterInitializationFailure(LoadedPlugins loadedPlugins, Throwable initializationFailure) {
    try {
      loadedPlugins.close();
    } catch (IOException closeFailure) {
      initializationFailure.addSuppressed(closeFailure);
    }
  }

  public void reset(Supplier<SchedulerResetConfiguration> pluginsWithConfigSupplier) {
    post(new ResetPluginsCommand(globalAnalysisContainer, analysisQueue, pluginsWithConfigSupplier));
  }

  public void wakeUp() {
    analysisQueue.wakeUp();
  }

  private void executeQueuedCommands() {
    try {
      while (termination.get() == null) {
        SonarLintLogger.get().setTarget(logOutput);
        try {
          var command = analysisQueue.takeNextCommand();
          executingCommand.set(command);
          try {
            commandDequeuedHook.accept(command);
            if (shouldStopBeforeExecuting(command)) {
              break;
            }
            command.execute(globalAnalysisContainer.get().getModuleRegistry());
          } finally {
            executingCommand.compareAndSet(command, null);
          }
        } catch (InterruptedException e) {
          if (termination.get() == null) {
            LOG.error("Analysis engine interrupted", e);
          }
        } catch (Exception e) {
          LOG.debug("Analysis command failed", e);
        }
      }
    } finally {
      var terminationAction = termination.get();
      if (terminationAction != null) {
        terminationAction.run();
      }
    }
  }

  private boolean shouldStopBeforeExecuting(Command command) {
    if (termination.get() == null) {
      return false;
    }
    if (executingCommand.compareAndSet(command, null)) {
      command.cancel();
    }
    return true;
  }

  public void post(Command command) {
    LOG.debug("Post: " + Thread.currentThread().getName() + " " + Thread.currentThread().threadId());
    LOG.debug("Posting command from Scheduler: " + command);
    if (termination.get() != null) {
      LOG.error("Analysis engine stopping, ignoring command");
      command.cancel();
      return;
    }
    if (!analysisThread.isAlive()) {
      LOG.error("Analysis engine not started, ignoring command");
      command.cancel();
      return;
    }
    var currentCommand = executingCommand.get();
    if (currentCommand != null && command.shouldCancelPost(currentCommand)) {
      LOG.debug("Cancelling queuing of command");
      currentCommand.cancel();
    }
    LOG.debug("Posting command from Scheduler to queue: " + command);
    analysisQueue.post(command);
  }

  public void stop() {
    if (!analysisThread.isAlive()) {
      return;
    }
    if (!termination.compareAndSet(null, () -> globalAnalysisContainer.get().stopComponents())) {
      // already terminating
      return;
    }
    var command = executingCommand.getAndSet(null);
    if (command != null) {
      command.cancel();
    }
    analysisThread.interrupt();
    analysisQueue.removeAll().forEach(Command::cancel);
    try {
      analysisThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Interrupted while waiting for analysis engine to stop", e);
    }
  }
}
