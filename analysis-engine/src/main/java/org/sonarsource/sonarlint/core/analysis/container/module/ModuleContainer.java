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
package org.sonarsource.sonarlint.core.analysis.container.module;

import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.IssueListener;
import org.sonarsource.sonarlint.core.analysis.api.ModuleFileEventNotifier;
import org.sonarsource.sonarlint.core.analysis.container.ComponentContainer;
import org.sonarsource.sonarlint.core.analysis.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.analysis.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.LanguageDetection;
import org.sonarsource.sonarlint.core.analysis.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.analysis.util.ProgressWrapper;

public class ModuleContainer extends ComponentContainer {

  public ModuleContainer(ComponentContainer parent) {
    super(parent);
  }

  @Override
  protected void doBeforeStart() {
    add(
      SonarLintModuleFileSystem.class,
      ModuleInputFileBuilder.class,
      FileMetadata.class,
      LanguageDetection.class,

      ModuleFileEventNotifier.class);
    getComponentByType(ExtensionInstaller.class).install(this, ContainerLifespan.MODULE);
  }

  public AnalysisResults analyze(AnalysisConfiguration configuration, IssueListener issueListener, ProgressWrapper progressWrapper) {
    AnalysisContainer analysisContainer = new AnalysisContainer(this);
    analysisContainer.add(progressWrapper);
    analysisContainer.add(configuration);
    analysisContainer.add(issueListener);
    AnalysisResults analysisResult = new AnalysisResults();
    analysisContainer.add(analysisResult);
    analysisContainer.add(new ActiveRulesAdapter(configuration.activeRules().stream().map(ActiveRuleAdapter::new).collect(Collectors.toList())));
    analysisContainer.execute();
    return analysisResult;
  }

}
