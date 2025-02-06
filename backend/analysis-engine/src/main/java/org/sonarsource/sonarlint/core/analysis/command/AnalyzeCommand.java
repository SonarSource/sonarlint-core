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

import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.Trace;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

import static org.sonarsource.sonarlint.core.commons.util.StringUtils.pluralize;

public class AnalyzeCommand implements Command<AnalysisResults> {
  @Nullable
  private final Object moduleKey;
  private final AnalysisConfiguration configuration;
  private final Consumer<Issue> issueListener;
  private final LogOutput logOutput;
  @Nullable
  private final Trace trace;

  public AnalyzeCommand(@Nullable Object moduleKey, AnalysisConfiguration configuration, Consumer<Issue> issueListener, @Nullable LogOutput logOutput, @Nullable Trace trace) {
    this.moduleKey = moduleKey;
    this.configuration = configuration;
    this.issueListener = issueListener;
    this.logOutput = logOutput;
    this.trace = trace;
  }

  @Override
  public AnalysisResults execute(ModuleRegistry moduleRegistry, ProgressMonitor progressMonitor) {
    if (logOutput != null) {
      SonarLintLogger.get().setTarget(logOutput);
    }
    return progressMonitor.startTask("Analyzing " + pluralize(configuration.inputFiles().size(), "file"),
      () -> doRunAnalysis(moduleRegistry, progressMonitor));
  }

  private AnalysisResults doRunAnalysis(ModuleRegistry moduleRegistry, ProgressMonitor progressMonitor) {
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
}
