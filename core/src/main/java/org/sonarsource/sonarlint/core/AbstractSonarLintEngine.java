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
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.loading.LoadedPlugins;
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
    getAnalysisEngine().registerModule(module);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> stopModule(Object moduleKey) {
    getAnalysisEngine().unregisterModule(moduleKey);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    getAnalysisEngine().fireModuleFileEvent(moduleKey, event);
    return CompletableFuture.completedFuture(null);
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

}
