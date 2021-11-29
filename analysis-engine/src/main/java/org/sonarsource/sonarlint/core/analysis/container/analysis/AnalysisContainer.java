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

import java.util.HashMap;
import java.util.Map;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.resources.Languages;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
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
import org.sonarsource.sonarlint.core.analysis.container.global.MapSettings;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainer;

public class AnalysisContainer extends ComponentContainer {

  private static final Logger LOG = Loggers.get(AnalysisContainer.class);
  private final AnalysisConfiguration analysisConfig;

  public AnalysisContainer(ModuleContainer parent, AnalysisConfiguration analysisConfig) {
    super(parent);
    this.analysisConfig = analysisConfig;
  }

  @Override
  protected void doBeforeStart() {
    MapSettings settings = buildAnalysisSettings(analysisConfig);

    add(
      analysisConfig,
      settings,
      settings.asConfig(),

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

    // Add analysis level plugin extensions
    getComponentByType(AnalysisExtensionInstaller.class).install(this, ContainerLifespan.ANALYSIS);
  }

  private MapSettings buildAnalysisSettings(AnalysisConfiguration analysisConfig) {
    Map<String, String> props = new HashMap<>();
    props.putAll(getParent().getComponentByType(AnalysisEngineConfiguration.class).getEffectiveConfig());
    props.putAll(analysisConfig.extraProperties());
    return new MapSettings(getPropertyDefinitions(), props);
  }

  @Override
  protected void doAfterStart() {
    LOG.debug("Start analysis");
    // Don't initialize Sensors before the FS is indexed
    getComponentByType(FileIndexer.class).index();
    getComponentByType(SensorsExecutor.class).execute();
  }

}
