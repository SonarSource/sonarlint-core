/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

public abstract class AbstractSonarLintEngine implements SonarLintEngine {
  protected static final SonarLintLogger LOG = SonarLintLogger.get();

  // Visible for medium tests
  public abstract AnalysisEngine getAnalysisEngine();

  protected final ClientLogOutput logOutput;

  protected AbstractSonarLintEngine(@Nullable ClientLogOutput logOutput) {
    this.logOutput = logOutput;
  }

  @Override
  public CompletableFuture<Void> declareModule(ClientModuleInfo module) {
    return getAnalysisEngine().post(new RegisterModuleCommand(module), new ProgressMonitor(null));
  }

  @Override
  public CompletableFuture<Void> stopModule(Object moduleKey) {
    return getAnalysisEngine().post(new UnregisterModuleCommand(moduleKey), new ProgressMonitor(null));
  }

  @Override
  public CompletableFuture<Void> fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    return getAnalysisEngine().post(new NotifyModuleEventCommand(moduleKey, event), new ProgressMonitor(null));
  }

  protected static Map<String, SonarLintRuleDefinition> loadPluginMetadata(LoadedPlugins loadedPlugins, Set<Language> enabledLanguages,
    boolean includeTemplateRules, boolean hotspotsEnabled) {
    var ruleExtractor = new RulesDefinitionExtractor();
    return ruleExtractor.extractRules(loadedPlugins.getPluginInstancesByKeys(), enabledLanguages, includeTemplateRules, hotspotsEnabled).stream()
      .collect(Collectors.toMap(SonarLintRuleDefinition::getKey, r -> r));
  }

  protected void setLogging(@Nullable ClientLogOutput logOutput) {
    if (logOutput != null) {
      SonarLintLogger.setTarget(logOutput);
    } else {
      SonarLintLogger.setTarget(this.logOutput);
    }
  }

  protected AnalysisResults postAnalysisCommandAndGetResult(AnalyzeCommand analyzeCommand, @Nullable ClientProgressMonitor monitor) {
    try {
      var analysisResults = getAnalysisEngine().post(analyzeCommand, new ProgressMonitor(monitor)).get();
      return analysisResults == null ? new AnalysisResults() : analysisResults;
    } catch (ExecutionException e) {
      throw SonarLintWrappedException.wrap(e.getCause());
    } catch (Exception e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

}
