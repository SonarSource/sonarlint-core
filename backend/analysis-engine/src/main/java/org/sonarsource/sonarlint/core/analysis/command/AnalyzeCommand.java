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
package org.sonarsource.sonarlint.core.analysis.command;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.Trace;
import org.sonarsource.sonarlint.core.commons.progress.ProgressIndicator;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.progress.TaskManager;

import static org.sonarsource.sonarlint.core.commons.util.StringUtils.pluralize;

public class AnalyzeCommand extends Command {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final String moduleKey;
  private final UUID analysisId;
  private final TriggerType triggerType;
  private final Supplier<AnalysisConfiguration> configurationSupplier;
  private final Consumer<Issue> issueListener;
  @Nullable
  private final Trace trace;
  private final CompletableFuture<AnalysisResults> futureResult;
  private final SonarLintCancelMonitor cancelMonitor;
  private final TaskManager taskManager;
  private final Consumer<List<ClientInputFile>> analysisStarted;
  private final Supplier<Boolean> isReadySupplier;
  private final List<URI> files;
  private final Map<String, String> extraProperties;

  public AnalyzeCommand(String moduleKey, UUID analysisId, TriggerType triggerType, Supplier<AnalysisConfiguration> configurationSupplier, Consumer<Issue> issueListener,
    @Nullable Trace trace, SonarLintCancelMonitor cancelMonitor, TaskManager taskManager, Consumer<List<ClientInputFile>> analysisStarted, Supplier<Boolean> isReadySupplier,
    List<URI> files, Map<String, String> extraProperties) {
    this(moduleKey, analysisId, triggerType, configurationSupplier, issueListener, trace, cancelMonitor, taskManager, analysisStarted, isReadySupplier, files, extraProperties,
      new CompletableFuture<>());

  }

  public AnalyzeCommand(String moduleKey, UUID analysisId, TriggerType triggerType, Supplier<AnalysisConfiguration> configurationSupplier, Consumer<Issue> issueListener,
    @Nullable Trace trace, SonarLintCancelMonitor cancelMonitor, TaskManager taskManager, Consumer<List<ClientInputFile>> analysisStarted, Supplier<Boolean> isReadySupplier,
    List<URI> files, Map<String, String> extraProperties, CompletableFuture<AnalysisResults> futureResult) {
    this.moduleKey = moduleKey;
    this.analysisId = analysisId;
    this.triggerType = triggerType;
    this.configurationSupplier = configurationSupplier;
    this.issueListener = issueListener;
    this.trace = trace;
    this.cancelMonitor = cancelMonitor;
    this.taskManager = taskManager;
    this.analysisStarted = analysisStarted;
    this.isReadySupplier = isReadySupplier;
    this.files = files;
    this.extraProperties = extraProperties;
    this.futureResult = futureResult;
  }

  @Override
  public boolean isReady() {
    return isReadySupplier.get();
  }

  public String getModuleKey() {
    return moduleKey;
  }

  public TriggerType getTriggerType() {
    return triggerType;
  }

  public CompletableFuture<AnalysisResults> getFutureResult() {
    return futureResult;
  }

  public List<URI> getFiles() {
    return files;
  }

  public Map<String, String> getExtraProperties() {
    return extraProperties;
  }

  @Override
  public void execute(ModuleRegistry moduleRegistry) {
    try {
      var configuration = configurationSupplier.get();
      taskManager.runTask(moduleKey, analysisId, "Analyzing " + pluralize(configuration.inputFiles().size(), "file"), null, true, false,
        indicator -> execute(moduleRegistry, indicator, configuration), cancelMonitor);
    } catch (Exception e) {
      handleAnalysisFailed(e);
    }
  }

  void execute(ModuleRegistry moduleRegistry, ProgressIndicator progressIndicator, AnalysisConfiguration configuration) {
    try {
      doExecute(moduleRegistry, progressIndicator, configuration);
    } catch (Exception e) {
      handleAnalysisFailed(e);
    }
  }

  void doExecute(ModuleRegistry moduleRegistry, ProgressIndicator progressIndicator, AnalysisConfiguration analysisConfig) {
    if (analysisConfig.inputFiles().isEmpty()) {
      LOG.info("No file to analyze");
      futureResult.complete(new AnalysisResults());
      return;
    }
    try {
      LOG.info("Starting analysis with configuration: {}", analysisConfig);
      var analysisResults = doRunAnalysis(moduleRegistry, progressIndicator, analysisConfig);
      futureResult.complete(analysisResults);
    } catch (CompletionException e) {
      handleAnalysisFailed(e.getCause());
    } catch (Exception e) {
      handleAnalysisFailed(e);
    }
  }

  private void handleAnalysisFailed(Throwable throwable) {
    LOG.error("Error during analysis", throwable);
    futureResult.completeExceptionally(throwable);
  }

  private AnalysisResults doRunAnalysis(ModuleRegistry moduleRegistry, ProgressIndicator progressIndicator, AnalysisConfiguration configuration) {
    var startTime = System.currentTimeMillis();
    analysisStarted.accept(configuration.inputFiles());
    var moduleContainer = moduleRegistry.getContainerFor(moduleKey);
    if (moduleContainer == null) {
      LOG.info("No module found for key '" + moduleKey + "', skipping analysis");
      return new AnalysisResults();
    }
    if (trace != null) {
      trace.setData("filesCount", configuration.inputFiles().size());
      trace.setData("languages", configuration.inputFiles().stream()
        .map(ClientInputFile::language)
        .filter(Objects::nonNull)
        .map(SonarLanguage::getSonarLanguageKey)
        .toList());
    }
    try {
      var result = moduleContainer.analyze(configuration, issueListener, progressIndicator, trace);
      if (trace != null) {
        trace.setData("failedFilesCount", result.failedAnalysisFiles().size());
        trace.finishSuccessfully();
      }
      result.setDuration(Duration.ofMillis(System.currentTimeMillis() - startTime));
      return result;
    } catch (Throwable e) {
      if (trace != null) {
        trace.finishExceptionally(e);
      }
      throw e;
    }
  }

  public AnalyzeCommand mergeWith(AnalyzeCommand otherNewerAnalyzeCommand) {
    var analysisConfiguration = configurationSupplier.get();
    var newerAnalysisConfiguration = otherNewerAnalyzeCommand.configurationSupplier.get();
    var mergedInputFiles = new ArrayList<>(newerAnalysisConfiguration.inputFiles());
    var newInputFileUris = newerAnalysisConfiguration.inputFiles().stream().map(ClientInputFile::uri).collect(Collectors.toSet());
    for (ClientInputFile inputFile : analysisConfiguration.inputFiles()) {
      if (!newInputFileUris.contains(inputFile.uri())) {
        mergedInputFiles.add(inputFile);
      }
    }
    var mergedAnalysisConfiguration = AnalysisConfiguration.builder()
      .addActiveRules(newerAnalysisConfiguration.activeRules())
      .setBaseDir(newerAnalysisConfiguration.baseDir())
      .putAllExtraProperties(newerAnalysisConfiguration.extraProperties())
      .addInputFiles(mergedInputFiles)
      .build();
    return new AnalyzeCommand(moduleKey, analysisId, triggerType, () -> mergedAnalysisConfiguration, issueListener, trace, new SonarLintCancelMonitor(), taskManager,
      analysisStarted, isReadySupplier, mergedInputFiles.stream().map(ClientInputFile::uri).toList(), newerAnalysisConfiguration.extraProperties(), futureResult);
  }

  @Override
  public void cancel() {
    cancelMonitor.cancel();
    futureResult.cancel(true);
  }

  @Override
  public boolean shouldCancel(Command executingCommand) {
    if (!(executingCommand instanceof AnalyzeCommand analyzeCommand)) {
      return false;
    }
    var triggerTypesMatch = getTriggerType() == analyzeCommand.getTriggerType();
    var filesMatch = Objects.equals(getFiles(), analyzeCommand.getFiles());
    var extraPropertiesMatch = Objects.equals(getExtraProperties(), analyzeCommand.getExtraProperties());
    return triggerTypesMatch && filesMatch && extraPropertiesMatch;
  }
}
