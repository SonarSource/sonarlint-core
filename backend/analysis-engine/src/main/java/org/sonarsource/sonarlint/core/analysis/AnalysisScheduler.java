/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis;

import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.command.Command;
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
    startContainer(analysisGlobalConfig, loadedPlugins);
    analysisThread.start();
  }

  public void reset(AnalysisSchedulerConfiguration analysisGlobalConfig, LoadedPlugins loadedPlugins) {
    // recreate the context
    globalAnalysisContainer.get().stopComponents();
    startContainer(analysisGlobalConfig, loadedPlugins);
    analysisQueue.clearAllButAnalyses();
  }

  private void startContainer(AnalysisSchedulerConfiguration analysisGlobalConfig, LoadedPlugins loadedPlugins) {
    globalAnalysisContainer.set(new GlobalAnalysisContainer(analysisGlobalConfig, loadedPlugins));
    globalAnalysisContainer.get().startComponents();
  }

  public void wakeUp() {
    analysisQueue.wakeUp();
  }

  private void executeQueuedCommands() {
    while (termination.get() == null) {
      SonarLintLogger.get().setTarget(logOutput);
      try {
        executingCommand.set(analysisQueue.takeNextCommand());
        if (termination.get() == CANCELING_TERMINATION) {
          executingCommand.getAndSet(null).cancel();
          break;
        }
        executingCommand.get().execute(globalAnalysisContainer.get().getModuleRegistry());
        executingCommand.set(null);
      } catch (InterruptedException e) {
        if (termination.get() != CANCELING_TERMINATION) {
          LOG.error("Analysis engine interrupted", e);
        }
      }
    }
    termination.get().run();
  }

  public void post(Command command) {
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
    if (currentCommand != null && command.shouldCancel(currentCommand)) {
      LOG.debug("Cancelling execution of similar analysis");
      executingCommand.set(null);
      currentCommand.cancel();
    }
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
