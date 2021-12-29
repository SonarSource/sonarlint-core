/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
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
  public void declareModule(ClientModuleInfo module) {
    try {
      getAnalysisEngine().post(new RegisterModuleCommand(module), new ProgressMonitor(null)).get();
    } catch (Exception e) {
      LOG.error("Error declaring module '{}'", module.key() + "'", e);
    }
  }

  @Override
  public void stopModule(Object moduleKey) {
    try {
      getAnalysisEngine().post(new UnregisterModuleCommand(moduleKey), new ProgressMonitor(null)).get();
    } catch (Exception e) {
      LOG.error("Error stopping module '{}'", moduleKey + "'", e);
    }
  }

  @Override
  public void fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    try {
      getAnalysisEngine().post(new NotifyModuleEventCommand(moduleKey, event), new ProgressMonitor(null)).get();
    } catch (Exception e) {
      LOG.error("Error notifying module '{}' of event", moduleKey, e);
    }
  }

  protected static Map<String, SonarLintRuleDefinition> loadPluginMetadata(PluginInstancesRepository pluginInstancesRepository, Set<Language> enabledLanguages,
    boolean includeTemplateRules) {
    var ruleExtractor = new RulesDefinitionExtractor();
    return ruleExtractor.extractRules(pluginInstancesRepository, enabledLanguages, includeTemplateRules).stream()
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
