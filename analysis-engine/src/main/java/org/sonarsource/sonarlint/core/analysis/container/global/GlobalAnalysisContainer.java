/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.time.Clock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.IssueListenerHolder;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.analysis.sonarapi.ActiveRuleAdapter;
import org.sonarsource.sonarlint.core.analysis.sonarapi.ActiveRulesAdapter;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.ApiVersions;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.pico.ComponentContainer;
import org.sonarsource.sonarlint.core.plugin.commons.sonarapi.SonarLintRuntimeImpl;

public class GlobalAnalysisContainer extends ComponentContainer {

  private GlobalExtensionContainer globalExtensionContainer;
  private ModuleRegistry moduleRegistry;
  private final AnalysisEngineConfiguration analysisGlobalConfig;
  private final PluginInstancesRepository pluginInstancesRepository;

  public GlobalAnalysisContainer(AnalysisEngineConfiguration analysisGlobalConfig, PluginInstancesRepository pluginInstancesRepository) {
    this.analysisGlobalConfig = analysisGlobalConfig;
    this.pluginInstancesRepository = pluginInstancesRepository;
  }

  @Override
  protected void doBeforeStart() {
    var sonarPluginApiVersion = ApiVersions.loadSonarPluginApiVersion();
    var sonarlintPluginApiVersion = ApiVersions.loadSonarLintPluginApiVersion();

    add(
      analysisGlobalConfig,
      pluginInstancesRepository,
      GlobalSettings.class,
      new GlobalConfigurationProvider(),
      AnalysisExtensionInstaller.class,
      new SonarQubeVersion(sonarPluginApiVersion),
      new SonarLintRuntimeImpl(sonarPluginApiVersion, sonarlintPluginApiVersion, analysisGlobalConfig.getClientPid()),

      new GlobalTempFolderProvider(),
      UriReader.class,
      Clock.systemDefaultZone(),
      System2.INSTANCE);
  }

  @Override
  protected void doAfterStart() {
    declarePluginProperties();
    globalExtensionContainer = new GlobalExtensionContainer(this);
    globalExtensionContainer.startComponents();
    this.moduleRegistry = new ModuleRegistry(globalExtensionContainer, analysisGlobalConfig.getModulesProvider());
  }

  @Override
  public ComponentContainer stopComponents(boolean swallowException) {
    try {
      if (moduleRegistry != null) {
        moduleRegistry.stopAll();
      }
      if (globalExtensionContainer != null) {
        globalExtensionContainer.stopComponents(swallowException);
      }
    } finally {
      super.stopComponents(swallowException);
    }
    return this;
  }

  private void declarePluginProperties() {
    PluginInstancesRepository pluginRepository = getComponentByType(PluginInstancesRepository.class);
    pluginRepository.getPluginInstancesByKeys().values().forEach(this::declareProperties);
  }

  public AnalysisResults analyze(ModuleContainer moduleContainer, AnalysisConfiguration configuration, Consumer<Issue> issueListener, ProgressMonitor progress) {
    AnalysisContainer analysisContainer = new AnalysisContainer(moduleContainer, progress);
    analysisContainer.add(configuration);
    analysisContainer.add(new IssueListenerHolder(issueListener));
    analysisContainer.add(new ActiveRulesAdapter(configuration.activeRules().stream().map(ActiveRuleAdapter::new).collect(Collectors.toList())));
    AnalysisResults defaultAnalysisResult = new AnalysisResults();
    analysisContainer.add(defaultAnalysisResult);
    analysisContainer.execute();
    return defaultAnalysisResult;
  }

  public ModuleRegistry getModuleRegistry() {
    return moduleRegistry;
  }

}
