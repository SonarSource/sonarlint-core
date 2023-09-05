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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.Command;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.analysis.command.StartModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.StopModuleCommand;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalExtensionContainer;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.loading.LoadedPlugins;

public class AnalysisEngine {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Command<Void> stopCommand = new Command<>() {
    @Override
    public Void execute(ProgressMonitor progressMonitor) {
      globalAnalysisContainer.stopComponents();
      return null;
    }

    @CheckForNull
    @Override
    public ModuleContainer getModuleContainer() {
      return null;
    }
  };

  private final GlobalAnalysisContainer globalAnalysisContainer;
  private final BlockingQueue<AsyncCommand<?>> commandQueue = new LinkedBlockingQueue<>();
  private final ClientLogOutput logOutput;
  private final AtomicReference<AsyncCommand<?>> executingCommand = new AtomicReference<>();
  private final ModuleRegistry moduleRegistry;
  private final AtomicBoolean stopped = new AtomicBoolean();
  private final Thread analysisThread;

  public AnalysisEngine(AnalysisEngineConfiguration analysisGlobalConfig, LoadedPlugins loadedPlugins, @Nullable ClientLogOutput logOutput) {
    globalAnalysisContainer = new GlobalAnalysisContainer(analysisGlobalConfig, loadedPlugins);
    this.logOutput = logOutput;
    // if the container cannot be started, the thread won't be started
    globalAnalysisContainer.startComponents();
    var globalExtensionContainer = new GlobalExtensionContainer(globalAnalysisContainer);
    globalExtensionContainer.startComponents();
    this.moduleRegistry = new ModuleRegistry(globalExtensionContainer, analysisGlobalConfig.getModulesProvider());
    analysisThread = new Thread(this::executeQueuedCommands, "sonarlint-analysis-engine");
    analysisThread.start();
  }

  private void executeQueuedCommands() {
    while (true) {
      SonarLintLogger.setTarget(logOutput);
      try {
        executingCommand.set(commandQueue.take());
        executingCommand.get().execute();
        if (executingCommand.get().command == stopCommand) {
          break;
        }
        executingCommand.set(null);
      } catch (InterruptedException e) {
        LOG.error("Analysis engine interrupted", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  public void registerModule(ClientModuleInfo module) {
    var moduleContainer = getModuleRegistry().registerModule(module);
    post(new StartModuleCommand(moduleContainer), new ProgressMonitor(null));
  }

  public void unregisterModule(Object moduleKey) {
    // Remove the module from the registry to allow a new module with the same key to be created, and prevent new commands to be queued for this
    // module
    var moduleContainer = getModuleRegistry().unregisterModule(moduleKey);
    if (moduleContainer == null) {
      // Method already called?
      return;
    }
    // Cancel pending tasks for the module
    commandQueue.forEach(c -> {
      if (c.command.getModuleContainer() == moduleContainer) {
        c.cancel();
      }
    });
    // Attempt to cancel the current task if it is part of the current module
    var currentCommand = executingCommand.get();
    if (currentCommand != null && currentCommand.command.getModuleContainer() == moduleContainer) {
      currentCommand.cancel();
    }
    post(new StopModuleCommand(moduleContainer), new ProgressMonitor(null));
  }

  public void fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    var moduleContainer = getModuleContainerOrFail(moduleKey);
    post(new NotifyModuleEventCommand(moduleContainer, event), new ProgressMonitor(null));
  }

  /**
   * @throws CancellationException if progressMonitor is cancelled
   */
  public CompletableFuture<AnalysisResults> analyze(@Nullable Object moduleKey, AnalysisConfiguration configuration, Consumer<Issue> issueListener,
    @Nullable ClientLogOutput logOutput,
    ProgressMonitor progressMonitor) {
    ModuleContainer moduleContainer = null;
    if (moduleKey != null) {
      moduleContainer = getModuleContainerOrFail(moduleKey);
    }
    var analyzeCommand = new AnalyzeCommand(getModuleRegistry(), moduleContainer, configuration, issueListener, logOutput);
    return post(analyzeCommand, progressMonitor);
  }

  private ModuleContainer getModuleContainerOrFail(Object moduleKey) {
    ModuleContainer moduleContainer;
    moduleContainer = getModuleRegistry().getContainerFor(moduleKey);
    if (moduleContainer == null) {
      throw new IllegalStateException("No module registered for key '" + moduleKey + "'");
    }
    return moduleContainer;
  }

  private <T> CompletableFuture<T> post(Command<T> command, ProgressMonitor progressMonitor) {
    if (stopped.get() && command != stopCommand) {
      return CompletableFuture.failedFuture(new IllegalStateException("Engine is stopped"));
    }
    var asyncCommand = new AsyncCommand<>(command, progressMonitor);
    commandQueue.add(asyncCommand);
    return asyncCommand.future;
  }

  public CompletableFuture<Void> stop() {
    // Prevent new commands to be submitted
    stopped.set(true);

    // Cancel pending commands
    List<AsyncCommand<?>> pendingCommands = new ArrayList<>();
    commandQueue.drainTo(pendingCommands);
    pendingCommands.forEach(AsyncCommand::cancel);

    // Cancel currently running command
    var asyncCommand = executingCommand.get();
    if (asyncCommand != null) {
      asyncCommand.cancel();
    }

    return post(stopCommand, new ProgressMonitor(null));
  }

  // Visible for medium tests
  public ModuleRegistry getModuleRegistry() {
    return moduleRegistry;
  }

  public static class AsyncCommand<T> {
    private final CompletableFuture<T> future = new CompletableFuture<>();
    private final Command<T> command;
    private final ProgressMonitor progressMonitor;

    public AsyncCommand(Command<T> command, ProgressMonitor progressMonitor) {
      this.command = command;
      this.progressMonitor = progressMonitor;
    }

    public void execute() {
      if (progressMonitor.isCanceled()) {
        future.cancel(false);
        return;
      }
      try {
        var result = command.execute(progressMonitor);
        if (progressMonitor.isCanceled()) {
          future.cancel(false);
        } else {
          future.complete(result);
        }
      } catch (Throwable e) {
        future.completeExceptionally(e);
      }
    }

    public void cancel() {
      progressMonitor.cancel();
      future.cancel(false);
    }
  }
}
