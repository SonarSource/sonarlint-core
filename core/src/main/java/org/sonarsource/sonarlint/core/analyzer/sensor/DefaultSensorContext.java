/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import java.io.Serializable;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputModule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.coverage.NewCoverage;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.error.NewAnalysisError;
import org.sonar.api.batch.sensor.error.internal.DefaultAnalysisError;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.internal.DefaultIssue;
import org.sonar.api.batch.sensor.measure.NewMeasure;
import org.sonar.api.batch.sensor.symbol.NewSymbolTable;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.analyzer.sensor.noop.NoOpNewCoverage;
import org.sonarsource.sonarlint.core.analyzer.sensor.noop.NoOpNewCpdTokens;
import org.sonarsource.sonarlint.core.analyzer.sensor.noop.NoOpNewHighlighting;
import org.sonarsource.sonarlint.core.analyzer.sensor.noop.NoOpNewMeasure;
import org.sonarsource.sonarlint.core.analyzer.sensor.noop.NoOpNewSymbolTable;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class DefaultSensorContext implements SensorContext {

  private static final NoOpNewHighlighting NO_OP_NEW_HIGHLIGHTING = new NoOpNewHighlighting();
  private static final NoOpNewSymbolTable NO_OP_NEW_SYMBOL_TABLE = new NoOpNewSymbolTable();
  private static final NoOpNewCpdTokens NO_OP_NEW_CPD_TOKENS = new NoOpNewCpdTokens();
  private static final NoOpNewCoverage NO_OP_NEW_COVERAGE = new NoOpNewCoverage();

  private final Settings settings;
  private final FileSystem fs;
  private final ActiveRules activeRules;
  private final SensorStorage sensorStorage;
  private final InputModule module;
  private final SonarRuntime sqRuntime;
  private final ProgressWrapper progress;
  private final Configuration config;

  public DefaultSensorContext(InputModule module, Settings settings, Configuration config, FileSystem fs, ActiveRules activeRules, SensorStorage sensorStorage,
    SonarRuntime sqRuntime, ProgressWrapper progress) {
    this.module = module;
    this.settings = settings;
    this.config = config;
    this.fs = fs;
    this.activeRules = activeRules;
    this.sensorStorage = sensorStorage;
    this.sqRuntime = sqRuntime;
    this.progress = progress;
  }

  @Override
  public Settings settings() {
    return settings;
  }

  @Override
  public Configuration config() {
    return config;
  }

  @Override
  public FileSystem fileSystem() {
    return fs;
  }

  @Override
  public ActiveRules activeRules() {
    return activeRules;
  }

  @Override
  public <G extends Serializable> NewMeasure<G> newMeasure() {
    return new NoOpNewMeasure<>();
  }

  @Override
  public NewIssue newIssue() {
    return new DefaultIssue(sensorStorage);
  }

  @Override
  public NewHighlighting newHighlighting() {
    return NO_OP_NEW_HIGHLIGHTING;
  }

  @Override
  public NewCoverage newCoverage() {
    return NO_OP_NEW_COVERAGE;
  }

  @Override
  public InputModule module() {
    return module;
  }

  @Override
  public Version getSonarQubeVersion() {
    return sqRuntime.getApiVersion();
  }

  @Override
  public SonarRuntime runtime() {
    return sqRuntime;
  }

  @Override
  public NewSymbolTable newSymbolTable() {
    return NO_OP_NEW_SYMBOL_TABLE;
  }

  @Override
  public NewCpdTokens newCpdTokens() {
    return NO_OP_NEW_CPD_TOKENS;
  }

  @Override
  public NewAnalysisError newAnalysisError() {
    return new DefaultAnalysisError(sensorStorage);
  }

  @Override
  public boolean isCancelled() {
    return progress.isCanceled();
  }

  @Override
  public void addContextProperty(String key, String value) {
    // NO OP
  }

  @Override
  public void markForPublishing(InputFile inputFile) {
    // NO OP
  }

}
