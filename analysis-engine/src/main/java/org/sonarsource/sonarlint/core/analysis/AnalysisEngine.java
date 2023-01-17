/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.command.Command;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;

public class AnalysisEngine {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final Runnable CANCELING_TERMINATION = () -> {
  };

  private final GlobalAnalysisContainer globalAnalysisContainer;
  private final BlockingQueue<AsyncCommand<?>> commandQueue = new LinkedBlockingQueue<>();
  private final Thread analysisThread = new Thread(this::executeQueuedCommands, "sonarlint-analysis-engine");
  private final ClientLogOutput logOutput;
  private final AtomicReference<Runnable> termination = new AtomicReference<>();
  private final AtomicReference<AsyncCommand<?>> executingCommand = new AtomicReference<>();

  public AnalysisEngine(AnalysisEngineConfiguration analysisGlobalConfig, LoadedPlugins loadedPlugins, @Nullable ClientLogOutput logOutput) {
    globalAnalysisContainer = new GlobalAnalysisContainer(analysisGlobalConfig, loadedPlugins);
    this.logOutput = logOutput;
    start();
  }

  private void start() {
    // if the container cannot be started, the thread won't be started
    globalAnalysisContainer.startComponents();
    analysisThread.start();
  }

  private void executeQueuedCommands() {
    while (termination.get() == null) {
      SonarLintLogger.setTarget(logOutput);
      try {
        executingCommand.set(commandQueue.take());
        if (termination.get() == CANCELING_TERMINATION) {
          executingCommand.get().cancel();
          break;
        }
        executingCommand.get().execute(getModuleRegistry());
        executingCommand.set(null);
      } catch (InterruptedException e) {
        if (termination.get() != CANCELING_TERMINATION) {
          LOG.error("Analysis engine interrupted", e);
        }
      }
    }
    termination.get().run();
  }

  public <T> CompletableFuture<T> post(Command<T> command, ProgressMonitor progressMonitor) {
    if (termination.get() != null) {
      LOG.error("Analysis engine stopping, ignoring command");
      return CompletableFuture.completedFuture(null);
    }
    if (!analysisThread.isAlive()) {
      LOG.error("Analysis engine not started, ignoring command");
      return CompletableFuture.completedFuture(null);
    }

    var asyncCommand = new AsyncCommand<>(command, progressMonitor);
    try {
      commandQueue.put(asyncCommand);
    } catch (InterruptedException e) {
      asyncCommand.future.completeExceptionally(e);
    }
    return asyncCommand.future;
  }

  public void finishGracefully() {
    termination.compareAndSet(null, this::honorPendingCommands);
  }

  private void honorPendingCommands() {
    List<AsyncCommand<?>> pendingCommands = new ArrayList<>();
    commandQueue.drainTo(pendingCommands);
    pendingCommands.forEach(c -> c.execute(getModuleRegistry()));
    globalAnalysisContainer.stopComponents();
  }

  public void stop() {
    if (!analysisThread.isAlive()) {
      return;
    }
    if (!termination.compareAndSet(null, CANCELING_TERMINATION)) {
      // already terminating
      return;
    }
    var command = executingCommand.get();
    if (command != null) {
      command.cancel();
    }
    analysisThread.interrupt();
    List<AsyncCommand<?>> pendingCommands = new ArrayList<>();
    commandQueue.drainTo(pendingCommands);
    pendingCommands.forEach(c -> c.future.cancel(false));
    globalAnalysisContainer.stopComponents();
  }

  // Visible for medium tests
  public ModuleRegistry getModuleRegistry() {
    return globalAnalysisContainer.getModuleRegistry();
  }

  // Visible for medium tests
  public GlobalAnalysisContainer getGlobalAnalysisContainer() {
    return globalAnalysisContainer;
  }

  public static class AsyncCommand<T> {
    private final CompletableFuture<T> future = new CompletableFuture<>();
    private final Command<T> command;
    private final ProgressMonitor progressMonitor;

    public AsyncCommand(Command<T> command, ProgressMonitor progressMonitor) {
      this.command = command;
      this.progressMonitor = progressMonitor;
    }

    public void execute(ModuleRegistry moduleRegistry) {
      try {
        var result = command.execute(moduleRegistry, progressMonitor);
        future.complete(result);
      } catch (Throwable e) {
        future.completeExceptionally(e);
      }
    }

    public void cancel() {
      progressMonitor.cancel();
    }
  }
}
