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
package org.sonarsource.sonarlint.core.analysis.container.module;

import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.analysis.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.IssueListenerHolder;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.LanguageDetection;
import org.sonarsource.sonarlint.core.analysis.container.global.AnalysisExtensionInstaller;
import org.sonarsource.sonarlint.core.analysis.sonarapi.ActiveRuleAdapter;
import org.sonarsource.sonarlint.core.analysis.sonarapi.ActiveRulesAdapter;
import org.sonarsource.sonarlint.core.analysis.sonarapi.SonarLintModuleFileSystem;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainer;

public class ModuleContainer extends SpringComponentContainer {

  private final boolean isTransient;

  public ModuleContainer(SpringComponentContainer parent, boolean isTransient) {
    super(parent);
    this.isTransient = isTransient;
  }

  @Override
  protected void doBeforeStart() {
    add(
      SonarLintModuleFileSystem.class,
      ModuleInputFileBuilder.class,
      FileMetadata.class,
      LanguageDetection.class,

      ModuleFileEventNotifier.class);
    getParent().getComponentByType(AnalysisExtensionInstaller.class).install(this, ContainerLifespan.MODULE);
  }

  public boolean isTransient() {
    return isTransient;
  }

  public AnalysisResults analyze(AnalysisConfiguration configuration, Consumer<Issue> issueListener, ProgressMonitor progress) {
    var analysisContainer = new AnalysisContainer(this, progress);
    analysisContainer.add(configuration);
    analysisContainer.add(new IssueListenerHolder(issueListener));
    analysisContainer.add(new ActiveRulesAdapter(configuration.activeRules().stream().map(ActiveRuleAdapter::new).collect(Collectors.toList())));
    var defaultAnalysisResult = new AnalysisResults();
    analysisContainer.add(defaultAnalysisResult);
    analysisContainer.execute();
    return defaultAnalysisResult;
  }
}
