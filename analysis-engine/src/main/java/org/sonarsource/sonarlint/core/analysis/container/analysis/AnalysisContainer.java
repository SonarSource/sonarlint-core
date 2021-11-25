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
package org.sonarsource.sonarlint.core.analysis.container.analysis;

import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.resources.Languages;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileIndexer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.InputFileBuilder;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.InputFileIndex;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.LanguageDetection;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintFileSystem;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputProject;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.IssueFilters;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.EnforceIssuesFilter;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.IgnoreIssuesFilter;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.SonarLintNoSonarFilter;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssueExclusionPatternInitializer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssueInclusionPatternInitializer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.scanner.IssueExclusionsLoader;
import org.sonarsource.sonarlint.core.analysis.container.analysis.noop.NoOpFileLinesContextFactory;
import org.sonarsource.sonarlint.core.analysis.container.analysis.noop.NoOpTestPlanBuilder;
import org.sonarsource.sonarlint.core.analysis.container.analysis.noop.NoOpTestableBuilder;
import org.sonarsource.sonarlint.core.analysis.container.analysis.perspectives.BatchPerspectives;
import org.sonarsource.sonarlint.core.analysis.container.analysis.sensor.DefaultSensorContext;
import org.sonarsource.sonarlint.core.analysis.container.analysis.sensor.SensorOptimizer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.sensor.SensorsExecutor;
import org.sonarsource.sonarlint.core.analysis.container.analysis.sensor.SonarLintSensorStorage;
import org.sonarsource.sonarlint.core.analysis.container.global.AnalysisExtensionInstaller;
import org.sonarsource.sonarlint.core.analysis.container.global.PluginApiConfigurationProvider;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainer;

public class AnalysisContainer extends ComponentContainer {

  private static final Logger LOG = Loggers.get(AnalysisContainer.class);

  public AnalysisContainer(ModuleContainer parent) {
    super(parent);
  }

  @Override
  protected void doBeforeStart() {
    addCoreComponents();
    addPluginExtensions();
  }

  private void addCoreComponents() {
    add(
      SensorsExecutor.class,
      SonarLintInputProject.class,
      NoOpFileLinesContextFactory.class,

      // temp
      new AnalysisTempFolderProvider(),

      // file system
      PathResolver.class,

      // tests
      NoOpTestPlanBuilder.class,
      NoOpTestableBuilder.class,

      // lang
      Languages.class,

      new PluginApiAnalysisSettingsProvider(),
      new PluginApiConfigurationProvider(),

      // file system
      InputFileIndex.class,
      InputFileBuilder.class,
      FileMetadata.class,
      LanguageDetection.class,
      FileIndexer.class,
      SonarLintFileSystem.class,

      // Exclusions using SQ/SC properties
      EnforceIssuesFilter.class,
      IgnoreIssuesFilter.class,
      IssueExclusionPatternInitializer.class,
      IssueInclusionPatternInitializer.class,
      IssueExclusionsLoader.class,

      SensorOptimizer.class,

      DefaultSensorContext.class,
      SonarLintSensorStorage.class,
      IssueFilters.class,

      // rules
      CheckFactory.class,

      // issues
      SonarLintNoSonarFilter.class,

      // Perspectives
      BatchPerspectives.class);
  }

  private void addPluginExtensions() {
    getComponentByType(AnalysisExtensionInstaller.class).install(this, ContainerLifespan.ANALYSIS);
  }

  @Override
  protected void doAfterStart() {
    LOG.debug("Start analysis");
    // Don't initialize Sensors before the FS is indexed
    getComponentByType(FileIndexer.class).index();
    getComponentByType(SensorsExecutor.class).execute();
  }

}
