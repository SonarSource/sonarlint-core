/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.internal.ApiVersion;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.resources.Languages;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analyzer.issue.IssuableFactory;
import org.sonarsource.sonarlint.core.analyzer.issue.IssueFilters;
import org.sonarsource.sonarlint.core.analyzer.noop.NoOpFileLinesContextFactory;
import org.sonarsource.sonarlint.core.analyzer.noop.NoOpHighlightableBuilder;
import org.sonarsource.sonarlint.core.analyzer.noop.NoOpSymbolizableBuilder;
import org.sonarsource.sonarlint.core.analyzer.noop.NoOpTestPlanBuilder;
import org.sonarsource.sonarlint.core.analyzer.noop.NoOpTestableBuilder;
import org.sonarsource.sonarlint.core.analyzer.perspectives.BatchPerspectives;
import org.sonarsource.sonarlint.core.analyzer.sensor.BatchExtensionDictionnary;
import org.sonarsource.sonarlint.core.analyzer.sensor.DefaultSensorContext;
import org.sonarsource.sonarlint.core.analyzer.sensor.DefaultSensorStorage;
import org.sonarsource.sonarlint.core.analyzer.sensor.LtsApiSensorContext;
import org.sonarsource.sonarlint.core.analyzer.sensor.PhaseExecutor;
import org.sonarsource.sonarlint.core.analyzer.sensor.SensorOptimizer;
import org.sonarsource.sonarlint.core.analyzer.sensor.SensorsExecutor;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.DefaultLanguagesRepository;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.FileIndexer;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.FileMetadata;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.InputFileBuilder;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.InputPathCache;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.LanguageDetection;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintFileSystem;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.EnforceIssuesFilter;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.IgnoreIssuesFilter;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssueExclusionPatternInitializer;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssueInclusionPatternInitializer;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;

public class AnalysisContainer extends ComponentContainer {

  private static final Logger LOG = Loggers.get(AnalysisContainer.class);

  public AnalysisContainer(ComponentContainer globalContainer) {
    super(globalContainer);
  }

  @Override
  protected void doBeforeStart() {
    addBatchComponents();
    addPluginExtensions();
  }

  private void addBatchComponents() {
    add(
      new ProjectProvider(),
      new DefaultInputModule("sonarlint"),
      SonarRuntimeImpl.forSonarLint(ApiVersion.load(System2.INSTANCE)),
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
      DefaultLanguagesRepository.class,

      AnalysisSettings.class,
      PhaseExecutor.class,
      SensorsExecutor.class,

      // file system
      InputPathCache.class,
      InputFileBuilder.class,
      FileMetadata.class,
      LanguageDetection.class,
      FileIndexer.class,
      SonarLintFileSystem.class,

      // Exclusions in connected mode
      ServerSettingsProvider.class,
      ExclusionFilters.class,
      EnforceIssuesFilter.class,
      IgnoreIssuesFilter.class,
      IssueExclusionPatternInitializer.class,
      IssueInclusionPatternInitializer.class,

      SensorOptimizer.class,

      DefaultSensorContext.class,
      DefaultSensorStorage.class,
      LtsApiSensorContext.class,
      BatchExtensionDictionnary.class,
      IssueFilters.class,

      // rules
      CheckFactory.class,

      // issues
      IssuableFactory.class,
      org.sonar.api.issue.NoSonarFilter.class,

      // Perspectives
      BatchPerspectives.class,
      NoOpHighlightableBuilder.class,
      NoOpSymbolizableBuilder.class);
  }

  private void addPluginExtensions() {
    getComponentByType(ExtensionInstaller.class).install(this);
  }

  @Override
  protected void doAfterStart() {
    LOG.debug("Start analysis");
    getComponentByType(PhaseExecutor.class).execute();
  }

}
