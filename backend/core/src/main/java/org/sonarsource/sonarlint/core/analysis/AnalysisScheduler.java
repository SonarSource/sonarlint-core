/*
 * SonarLint Core - Implementation
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.MonitoringService;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.springframework.context.ApplicationEventPublisher;

public class AnalysisScheduler {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final Runnable CANCELING_TERMINATION = () -> {
  };
  private final LogOutput logOutput = SonarLintLogger.get().getTargetForCopy();
  private final AnalysisEngine engine;
  private final AnalysisTaskQueue analysisQueue = new AnalysisTaskQueue();
  private final AtomicReference<Runnable> termination = new AtomicReference<>();
  private final AtomicReference<AnalysisTask> executingTask = new AtomicReference<>();
  private final Thread analysisThread = new Thread(this::executeAnalysisTasks, "sonarlint-analysis-scheduler");
  private final AnalysisExecutor analysisExecutor;

  public AnalysisScheduler(AnalysisEngine engine, ConfigurationRepository configurationRepository, NodeJsService nodeJsService,
    UserAnalysisPropertiesRepository userAnalysisPropertiesRepository, StorageService storageService, PluginsService pluginsService, RulesRepository rulesRepository,
    RulesService rulesService, LanguageSupportRepository languageSupportRepository, ClientFileSystemService fileSystemService, MonitoringService monitoringService,
    FileExclusionService fileExclusionService, ClientFileSystemService clientFileSystemService, SonarLintRpcClient client,
    ConnectionConfigurationRepository connectionConfigurationRepository, boolean hotspotEnabled,
    ApplicationEventPublisher eventPublisher, Path esLintBridgeServerPath) {
    this.engine = engine;
    this.analysisExecutor = new AnalysisExecutor(configurationRepository, nodeJsService, userAnalysisPropertiesRepository, connectionConfigurationRepository, hotspotEnabled,
      storageService, pluginsService, rulesRepository, rulesService, languageSupportRepository, fileSystemService, monitoringService, fileExclusionService, clientFileSystemService,
      client, eventPublisher, esLintBridgeServerPath, engine);
    analysisThread.start();
  }

  private void executeAnalysisTasks() {
    while (termination.get() == null) {
      SonarLintLogger.get().setTarget(logOutput);
      try {
        executingTask.set(analysisQueue.takeNextTask());
        if (termination.get() == CANCELING_TERMINATION) {
          executingTask.get().getProgressMonitor().cancel();
          break;
        }
        var task = executingTask.get();
        analysisExecutor.execute(task);
        executingTask.set(null);
      } catch (InterruptedException e) {
        if (termination.get() != CANCELING_TERMINATION) {
          LOG.error("Analysis engine interrupted", e);
        }
      }
    }
    termination.get().run();
  }

  public CompletableFuture<AnalysisResults> schedule(AnalysisTask task) {
    try {
      analysisQueue.enqueue(task);
      return task.getResult();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return CompletableFuture.failedFuture(e);
    }
  }

  public void finishGracefully() {
    termination.compareAndSet(null, this::honorPendingTasks);
    engine.finishGracefully();
  }

  public void stop() {
    if (!analysisThread.isAlive()) {
      return;
    }
    if (!termination.compareAndSet(null, CANCELING_TERMINATION)) {
      // already terminating
      return;
    }
    var task = executingTask.get();
    if (task != null) {
      task.getProgressMonitor().cancel();
    }
    analysisThread.interrupt();
    List<AnalysisTask> pendingCommands = new ArrayList<>();
    analysisQueue.drainTo(pendingCommands);
    pendingCommands.forEach(c -> c.getResult().cancel(false));
    engine.stop();
  }

  private void honorPendingTasks() {
    // no-op for now, do we need it
  }

  public void registerModule(ClientModuleInfo moduleInfo) {
    engine.post(new RegisterModuleCommand(moduleInfo), new ProgressMonitor(null));
  }

  public void unregisterModule(String scopeId) {
    engine.post(new UnregisterModuleCommand(scopeId), new ProgressMonitor(null));
  }

  public void notifyModuleEvent(String scopeId, ClientFile file, ModuleFileEvent.Type type) {
    engine.post(new NotifyModuleEventCommand(scopeId,
      ClientModuleFileEvent.of(new BackendInputFile(file), type)), new ProgressMonitor(null)).join();
  }

  public void notifyScopeReady(String scopeId) {
    analysisQueue.markAsReady(scopeId);
  }
}
