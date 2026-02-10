/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.util.concurrent.atomic.AtomicReference;
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
  private static final Runnable CANCELING_TERMINATION = () -> {
  };

  private final AtomicReference<GlobalAnalysisContainer> globalAnalysisContainer = new AtomicReference<>();
  private final AnalysisQueue analysisQueue = new AnalysisQueue();
  private final Thread analysisThread = new Thread(this::executeQueuedCommands, "sonarlint-analysis-scheduler");
  private final LogOutput logOutput;
  private final AtomicReference<Runnable> termination = new AtomicReference<>();
  private final AtomicReference<Command> executingCommand = new AtomicReference<>();

  public AnalysisScheduler(AnalysisSchedulerConfiguration analysisGlobalConfig, LoadedPlugins loadedPlugins, @Nullable LogOutput logOutput) {
    this.logOutput = logOutput;
    // if the container cannot be started, the thread won't be started
    var analysisContainer = new GlobalAnalysisContainer(analysisGlobalConfig, loadedPlugins);
    analysisContainer.startComponents();
    globalAnalysisContainer.set(analysisContainer);
    analysisThread.start();
  }

  public void reset(AnalysisSchedulerConfiguration analysisGlobalConfig, Supplier<LoadedPlugins> pluginsSupplier) {
    post(new ResetPluginsCommand(analysisGlobalConfig, globalAnalysisContainer, analysisQueue, pluginsSupplier));
  }

  public void wakeUp() {
    analysisQueue.wakeUp();
  }

  private void executeQueuedCommands() {
    while (termination.get() == null) {
      SonarLintLogger.get().setTarget(logOutput);
      try {
        var command = analysisQueue.takeNextCommand();
        executingCommand.set(command);
        if (termination.get() == CANCELING_TERMINATION) {
          break;
        }
        executingCommand.get().execute(globalAnalysisContainer.get().getModuleRegistry());
        executingCommand.set(null);
      } catch (InterruptedException e) {
        if (termination.get() != CANCELING_TERMINATION) {
          LOG.error("Analysis engine interrupted", e);
        Thread.currentThread().interrupt();
        }
      } catch (Exception e) {
        LOG.debug("Analysis command failed", e);
      }
    }
    termination.get().run();
  }

  public void post(Command command) {
    LOG.debug("Post: " + Thread.currentThread().getName() + " " + Thread.currentThread().getId());
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
    if (!termination.compareAndSet(null, CANCELING_TERMINATION)) {
      // already terminating
      return;
    }
    var command = executingCommand.getAndSet(null);
    if (command != null) {
      command.cancel();
    }
    analysisThread.interrupt();
    analysisQueue.removeAll().forEach(Command::cancel);
    globalAnalysisContainer.get().stopComponents();
  }
}
