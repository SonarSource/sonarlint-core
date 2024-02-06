/*
 * SonarLint Core - Java Client Legacy
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.legacy.analysis;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.api.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetGlobalConfigurationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetGlobalConnectedConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsResponse;

import static java.util.Objects.requireNonNull;

public final class SonarLintAnalysisEngine {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-analysis-engine-restarter"));
  private final AtomicReference<AnalysisEngine> analysisEngine = new AtomicReference<>();

  @Nullable
  private final LogOutput logOutput;
  private final EngineConfiguration globalConfig;
  private final SonarLintRpcServer backend;
  @Nullable
  private final String connectionId;

  private Collection<PluginDetails> pluginDetails;

  public SonarLintAnalysisEngine(EngineConfiguration globalConfig, SonarLintRpcServer backend, @Nullable String connectionId) {
    this.logOutput = toCoreLogOutput(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;
    this.backend = backend;
    this.connectionId = connectionId;
    setLogging(null);
    restart();
  }

  /**
   * This method needs to be called when plugins have changed after a sync, or when the NodeJS path has changed.
   * As it is often called in reaction to a notification from the backend, we process it asynchronously to free the wire
   */
  public void restartAsync() {
    executorService.submit(() -> {
      setLogging(globalConfig.getLogOutput());
      restart();
      setLogging(null);
    });
  }

  private void restart() {
    CompletableFuture<GetGlobalConfigurationResponse> globalConfigFuture;
    if (connectionId == null) {
      globalConfigFuture = backend.getAnalysisService().getGlobalStandaloneConfiguration();
    } else {
      globalConfigFuture = backend.getAnalysisService().getGlobalConnectedConfiguration(new GetGlobalConnectedConfigurationParams(connectionId));
    }
    var globalConfigFromRpc = globalConfigFuture.join();

    var config = new PluginsLoader.Configuration(Set.copyOf(globalConfigFromRpc.getPluginPaths()),
      globalConfigFromRpc.getEnabledLanguages().stream().map(l -> SonarLanguage.valueOf(l.name())).collect(Collectors.toSet()),
      globalConfigFromRpc.isDataflowBugDetectionEnabled(),
      Optional.ofNullable(globalConfigFromRpc.getNodeJsVersion()).map(Version::create));
    var loadingResult = new PluginsLoader().load(config);

    pluginDetails = loadingResult.getPluginCheckResultByKeys().values().stream()
      .map(c -> new PluginDetails(c.getPlugin().getKey(), c.getPlugin().getName(), c.getPlugin().getVersion().toString(), c.getSkipReason().orElse(null)))
      .collect(Collectors.toList());

    var analysisGlobalConfig = AnalysisEngineConfiguration.builder()
      .setClientPid(globalConfig.getClientPid())
      .setExtraProperties(globalConfig.extraProperties())
      .setNodeJs(globalConfigFromRpc.getNodeJsPath())
      .setWorkDir(globalConfig.getWorkDir())
      .setModulesProvider(globalConfig.getModulesProvider())
      .build();
    var oldEngine = this.analysisEngine.getAndSet(new AnalysisEngine(analysisGlobalConfig, loadingResult.getLoadedPlugins(), logOutput));
    if (oldEngine != null) {
      oldEngine.finishGracefully();
    }
  }

  // only for medium tests
  public AnalysisEngine getAnalysisEngine() {
    return analysisEngine.get();
  }

  public CompletableFuture<Void> declareModule(ClientModuleInfo module) {
    return getAnalysisEngine().post(new RegisterModuleCommand(module), new ProgressMonitor(null));
  }

  public CompletableFuture<Void> stopModule(Object moduleKey) {
    return getAnalysisEngine().post(new UnregisterModuleCommand(moduleKey), new ProgressMonitor(null));
  }

  public CompletableFuture<Void> fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    return getAnalysisEngine().post(new NotifyModuleEventCommand(moduleKey, event), new ProgressMonitor(null));
  }

  private void setLogging(@Nullable ClientLogOutput logOutput) {
    if (logOutput != null) {
      SonarLintLogger.setTarget(toCoreLogOutput(logOutput));
    } else {
      SonarLintLogger.setTarget(this.logOutput);
    }
  }

  private AnalysisResults postAnalysisCommandAndGetResult(AnalyzeCommand analyzeCommand, @Nullable ClientProgressMonitor monitor) {
    try {
      var analysisResults = getAnalysisEngine().post(analyzeCommand, new ProgressMonitor(monitor)).get();
      return analysisResults == null ? new AnalysisResults() : analysisResults;
    } catch (ExecutionException e) {
      throw SonarLintWrappedException.wrap(e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new AnalysisResults();
    }
  }

  public AnalysisResults analyze(AnalysisConfiguration configuration, RawIssueListener rawIssueListener,
    @Nullable ClientLogOutput logOutput,
    @Nullable ClientProgressMonitor monitor, String configScopeId) {
    requireNonNull(configuration);
    requireNonNull(rawIssueListener);
    setLogging(logOutput);

    var configFromRpc = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(configScopeId)).join();

    var analysisConfig = org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration.builder()
      .addInputFiles(configuration.inputFiles())
      .putAllExtraProperties(configFromRpc.getAnalysisProperties())
      .putAllExtraProperties(configuration.extraProperties())
      .addActiveRules(configFromRpc.getActiveRules().stream().map(r -> {
        var ar = new org.sonarsource.sonarlint.core.analysis.api.ActiveRule(r.getRuleKey(), r.getLanguageKey());
        ar.setParams(r.getParams());
        ar.setTemplateRuleKey(r.getTemplateRuleKey());
        return ar;
      }).collect(Collectors.toList()))
      .setBaseDir(configuration.baseDir())
      .build();

    var ruleDetailsCache = new ConcurrentHashMap<String, GetRuleDetailsResponse>();

    var analyzeCommand = new AnalyzeCommand(configuration.moduleKey(), analysisConfig,
      i -> rawIssueListener.handle(
        new RawIssue(i, ruleDetailsCache.computeIfAbsent(i.getRuleKey(), k -> backend.getAnalysisService().getRuleDetails(new GetRuleDetailsParams(configScopeId, k)).join()))),
      toCoreLogOutput(logOutput));
    return postAnalysisCommandAndGetResult(analyzeCommand, monitor);
  }

  public void stop() {
    setLogging(null);
    try {
      getAnalysisEngine().stop();
    } catch (Exception e) {
      throw SonarLintWrappedException.wrap(e);
    }
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop analysis engine restarter in a timely manner");
    }
  }

  public Collection<PluginDetails> getPluginDetails() {
    return pluginDetails;
  }


  private static LogOutputAdapter toCoreLogOutput(@Nullable ClientLogOutput logOutput) {
    return logOutput == null ? null : new LogOutputAdapter(logOutput);
  }

  private static class LogOutputAdapter implements LogOutput {
    private final ClientLogOutput clientLogOutput;

    private LogOutputAdapter(ClientLogOutput clientLogOutput) {
      this.clientLogOutput = clientLogOutput;
    }

    @Override
    public void log(String formattedMessage, Level level) {
      clientLogOutput.log(formattedMessage, ClientLogOutput.Level.valueOf(level.name()));
    }
  }

}
