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
package org.sonarsource.sonarlint.core.container.analysis;

import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.resources.Languages;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonarsource.sonarlint.core.analysis.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.analysis.container.global.AnalysisExtensionInstaller;
import org.sonarsource.sonarlint.core.analysis.container.global.analysis.AnalysisTempFolderProvider;
import org.sonarsource.sonarlint.core.analyzer.issue.IssueFilters;
import org.sonarsource.sonarlint.core.analyzer.noop.NoOpFileLinesContextFactory;
import org.sonarsource.sonarlint.core.analyzer.sensor.DefaultSensorContext;
import org.sonarsource.sonarlint.core.analyzer.sensor.SensorOptimizer;
import org.sonarsource.sonarlint.core.analyzer.sensor.SensorsExecutor;
import org.sonarsource.sonarlint.core.analyzer.sensor.SonarLintSensorStorage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.FileIndexer;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.FileMetadata;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.InputFileBuilder;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.InputFileCache;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.LanguageDetection;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintFileSystem;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputProject;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.EnforceIssuesFilter;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.IgnoreIssuesFilter;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.SonarLintNoSonarFilter;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssueExclusionPatternInitializer;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssueInclusionPatternInitializer;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.scanner.IssueExclusionsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.pico.ComponentContainer;

public class AnalysisContainer extends ComponentContainer {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ProgressMonitor progress;

  public AnalysisContainer(ComponentContainer globalContainer, ProgressMonitor progress) {
    super(globalContainer);
    this.progress = progress;
  }

  @Override
  protected void doBeforeStart() {
    addCoreComponents();
    addPluginExtensions();
  }

  private void addCoreComponents() {
    add(
      progress,
      SonarLintInputProject.class,
      NoOpFileLinesContextFactory.class,

      // temp
      new AnalysisTempFolderProvider(),

      // file system
      PathResolver.class,

      // lang
      Languages.class,

      MutableAnalysisSettings.class,
      new AnalysisConfigurationProvider(),

      // file system
      InputFileCache.class,
      InputFileBuilder.class,
      FileMetadata.class,
      LanguageDetection.class,
      FileIndexer.class,
      SonarLintFileSystem.class,

      // Exclusions in connected mode
      ServerConfigurationProvider.class,
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
      SonarLintNoSonarFilter.class);
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
