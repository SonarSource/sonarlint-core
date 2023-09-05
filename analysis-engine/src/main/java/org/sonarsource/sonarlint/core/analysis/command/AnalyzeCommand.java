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
package org.sonarsource.sonarlint.core.analysis.command;

import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

public class AnalyzeCommand implements Command<AnalysisResults> {
  private final ModuleRegistry moduleRegistry;
  @Nullable
  private final ModuleContainer moduleContainer;
  private final AnalysisConfiguration configuration;
  private final Consumer<Issue> issueListener;
  private final ClientLogOutput logOutput;

  public AnalyzeCommand(ModuleRegistry moduleRegistry, @Nullable ModuleContainer moduleContainer, AnalysisConfiguration configuration, Consumer<Issue> issueListener,
    @Nullable ClientLogOutput logOutput) {
    this.moduleRegistry = moduleRegistry;
    this.moduleContainer = moduleContainer;
    this.configuration = configuration;
    this.issueListener = issueListener;
    this.logOutput = logOutput;
  }

  @CheckForNull
  @Override
  public ModuleContainer getModuleContainer() {
    return moduleContainer;
  }

  @Override
  public AnalysisResults execute(ProgressMonitor progressMonitor) {
    if (logOutput != null) {
      SonarLintLogger.setTarget(logOutput);
    }
    var isTransientContainer = moduleContainer == null;
    var containerToUse = isTransientContainer ? moduleRegistry.createTransientContainer(configuration.inputFiles()) : moduleContainer;
    Throwable originalException = null;
    try {
      return containerToUse.analyze(configuration, issueListener, progressMonitor);
    } catch (Throwable e) {
      originalException = e;
      throw e;
    } finally {
      try {
        if (isTransientContainer) {
          containerToUse.stopComponents();
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
