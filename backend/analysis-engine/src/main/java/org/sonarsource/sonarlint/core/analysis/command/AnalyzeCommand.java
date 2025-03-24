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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

import static org.sonarsource.sonarlint.core.commons.util.StringUtils.pluralize;

public class AnalyzeCommand extends Command {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final String moduleKey;
  private final TriggerType triggerType;
  private final Supplier<AnalysisConfiguration> configurationSupplier;
  private final Consumer<Issue> issueListener;
  @Nullable
  private final Trace trace;
  private final CompletableFuture<AnalysisResults> result = new CompletableFuture<>();
  private final ProgressMonitor progressMonitor;
  private final Consumer<List<ClientInputFile>> analysisStarted;
  private final Supplier<Boolean> isReadySupplier;

  public AnalyzeCommand(@Nullable String moduleKey, TriggerType triggerType, Supplier<AnalysisConfiguration> configurationSupplier, Consumer<Issue> issueListener,
    @Nullable Trace trace, ProgressMonitor progressMonitor, Consumer<List<ClientInputFile>> analysisStarted, Supplier<Boolean> isReadySupplier) {
    this.moduleKey = moduleKey;
    this.triggerType = triggerType;
    this.configurationSupplier = configurationSupplier;
    this.issueListener = issueListener;
    this.trace = trace;
    this.progressMonitor = progressMonitor;
    this.analysisStarted = analysisStarted;
    this.isReadySupplier = isReadySupplier;
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

  public CompletableFuture<AnalysisResults> getResult() {
    return result;
  }

  @Override
  public void execute(ModuleRegistry moduleRegistry) {
    try {
      var configuration = configurationSupplier.get();
      progressMonitor.startTask("Analyzing " + pluralize(configuration.inputFiles().size(), "file"),
        () -> execute(moduleRegistry, progressMonitor, configuration));
    } catch (Exception e) {
      handleAnalysisFailed(e);
    }
  }

  void execute(ModuleRegistry moduleRegistry, ProgressMonitor progressMonitor, AnalysisConfiguration configuration) {
    try {
      doExecute(moduleRegistry, progressMonitor, configuration);
    } catch (Exception e) {
      handleAnalysisFailed(e);
    }
  }

  void doExecute(ModuleRegistry moduleRegistry, ProgressMonitor progressMonitor, AnalysisConfiguration analysisConfig) {
    if (analysisConfig.inputFiles().isEmpty()) {
      LOG.info("No file to analyze");
      result.complete(new AnalysisResults());
      return;
    }
    try {
      var analysisResults = doRunAnalysis(moduleRegistry, progressMonitor, analysisConfig);
      result.complete(analysisResults);
    } catch (CompletionException e) {
      handleAnalysisFailed(e.getCause());
    } catch (Exception e) {
      handleAnalysisFailed(e);
    }
  }

  private void handleAnalysisFailed(Throwable throwable) {
    LOG.error("Error during analysis", throwable);
    result.completeExceptionally(throwable);
  }

  private AnalysisResults doRunAnalysis(ModuleRegistry moduleRegistry, ProgressMonitor progressMonitor, AnalysisConfiguration configuration) {
    analysisStarted.accept(configuration.inputFiles());
    var moduleContainer = moduleKey != null ? moduleRegistry.getContainerFor(moduleKey) : null;
    if (moduleContainer == null) {
      // if not found, means we are outside of any module (e.g. single file analysis on VSCode)
      moduleContainer = moduleRegistry.createTransientContainer(configuration.inputFiles());
    }
    Throwable originalException = null;
    if (trace != null) {
      trace.setData("filesCount", configuration.inputFiles().size());
      trace.setData("languages", configuration.inputFiles().stream()
        .map(ClientInputFile::language)
        .filter(Objects::nonNull)
        .map(SonarLanguage::getSonarLanguageKey)
        .toList());
    }
    try {
      var result = moduleContainer.analyze(configuration, issueListener, progressMonitor, trace);
      if (trace != null) {
        trace.setData("failedFilesCount", result.failedAnalysisFiles().size());
        trace.finishSuccessfully();
      }
      return result;
    } catch (Throwable e) {
      originalException = e;
      if (trace != null) {
        trace.finishExceptionally(e);
      }
      throw e;
    } finally {
      try {
        if (moduleContainer.isTransient()) {
          moduleContainer.stopComponents();
        }
      } catch (Exception e) {
        if (originalException != null) {
          e.addSuppressed(originalException);
        }
        throw e;
      }
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
    return new AnalyzeCommand(otherNewerAnalyzeCommand.moduleKey, otherNewerAnalyzeCommand.triggerType, () -> mergedAnalysisConfiguration, otherNewerAnalyzeCommand.issueListener,
      otherNewerAnalyzeCommand.trace, otherNewerAnalyzeCommand.progressMonitor, otherNewerAnalyzeCommand.analysisStarted, otherNewerAnalyzeCommand.isReadySupplier);
  }

  @Override
  public void cancel() {
    progressMonitor.cancel();
    result.cancel(true);
  }
}
