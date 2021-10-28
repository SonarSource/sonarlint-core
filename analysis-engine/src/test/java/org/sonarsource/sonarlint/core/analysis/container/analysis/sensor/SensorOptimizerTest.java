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
package org.sonarsource.sonarlint.core.analysis.container.analysis.sensor;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.Language;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.InputFileIndex;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintFileSystem;
import org.sonarsource.sonarlint.core.analysis.container.analysis.sensor.DefaultSensorDescriptor;
import org.sonarsource.sonarlint.core.analysis.container.analysis.sensor.SensorOptimizer;
import org.sonarsource.sonarlint.core.analysis.container.global.MapSettings;
import org.sonarsource.sonarlint.core.analysis.container.module.ActiveRuleAdapter;
import org.sonarsource.sonarlint.core.analysis.container.module.ActiveRulesAdapter;
import testutils.TestInputFileBuilder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SensorOptimizerTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private FileSystem fs;
  private final InputFileIndex inputFileCache = new InputFileIndex();

  @Before
  public void prepare() throws Exception {
    fs = new SonarLintFileSystem(mock(AnalysisConfiguration.class), inputFileCache);

  }

  @Test
  public void should_run_analyzer_with_no_metadata() {
    SensorOptimizer underTest = prepareSensorOptimizerWithConfig(Map.of());
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();

    assertThat(underTest.shouldExecute(descriptor)).isTrue();
  }

  @Test
  public void should_optimize_on_language() {
    SensorOptimizer underTest = prepareSensorOptimizerWithConfig(Map.of());
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
      .onlyOnLanguages("java", "php");
    assertThat(underTest.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.java").setLanguage(Language.JAVA).build());
    assertThat(underTest.shouldExecute(descriptor)).isTrue();
  }

  @Test
  public void should_optimize_on_type() {
    SensorOptimizer underTest = prepareSensorOptimizerWithConfig(Map.of());
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
      .onlyOnFileType(InputFile.Type.MAIN);
    assertThat(underTest.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("tests/FooTest.java").setType(InputFile.Type.TEST).build());
    assertThat(underTest.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.java").setType(InputFile.Type.MAIN).build());
    assertThat(underTest.shouldExecute(descriptor)).isTrue();
  }

  @Test
  public void should_optimize_on_both_type_and_language() {
    SensorOptimizer underTest = prepareSensorOptimizerWithConfig(Map.of());
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
      .onlyOnLanguages("java", "php")
      .onlyOnFileType(InputFile.Type.MAIN);
    assertThat(underTest.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("tests/FooTest.java").setLanguage(Language.JAVA).setType(InputFile.Type.TEST).build());
    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.cbl").setLanguage(Language.COBOL).setType(InputFile.Type.MAIN).build());
    assertThat(underTest.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.java").setLanguage(Language.JAVA).setType(InputFile.Type.MAIN).build());
    assertThat(underTest.shouldExecute(descriptor)).isTrue();
  }

  @Test
  public void should_optimize_on_repository() {
    SensorOptimizer underTestNoActiveRules = prepareSensorOptimizerWithActiveRules(new ActiveRulesAdapter(List.of()));
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
      .createIssuesForRuleRepositories("squid");
    assertThat(underTestNoActiveRules.shouldExecute(descriptor)).isFalse();

    ActiveRuleAdapter ruleAnotherRepo = mock(ActiveRuleAdapter.class);
    when(ruleAnotherRepo.ruleKey()).thenReturn(RuleKey.of("repo1", "foo"));
    ActiveRules activeRulesAnotherRepo = new ActiveRulesAdapter(asList(ruleAnotherRepo));
    SensorOptimizer underTestAnotherRepo = prepareSensorOptimizerWithActiveRules(activeRulesAnotherRepo);

    assertThat(underTestAnotherRepo.shouldExecute(descriptor)).isFalse();

    ActiveRuleAdapter ruleSquid = mock(ActiveRuleAdapter.class);
    when(ruleSquid.ruleKey()).thenReturn(RuleKey.of("squid", "rule"));
    ActiveRules activeRulesHaveRuleForRepo = new ActiveRulesAdapter(asList(ruleSquid, ruleAnotherRepo));
    SensorOptimizer underTestHaveRuleForRepo = prepareSensorOptimizerWithActiveRules(activeRulesHaveRuleForRepo);

    assertThat(underTestHaveRuleForRepo.shouldExecute(descriptor)).isTrue();
  }

  @Test
  public void should_optimize_on_settings() {
    SensorOptimizer underTest1 = prepareSensorOptimizerWithConfig(Map.of());
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
      .requireProperty("sonar.foo.reportPath");
    assertThat(underTest1.shouldExecute(descriptor)).isFalse();

    SensorOptimizer underTest2 = prepareSensorOptimizerWithConfig(Map.of("sonar.foo.reportPath", "foo"));
    assertThat(underTest2.shouldExecute(descriptor)).isTrue();
  }

  private SensorOptimizer prepareSensorOptimizerWithConfig(Map<String, String> config) {
    MapSettings settings = new MapSettings(config);
    SensorOptimizer underTest = new SensorOptimizer(fs, null, settings.asConfig());
    return underTest;
  }

  private SensorOptimizer prepareSensorOptimizerWithActiveRules(ActiveRules activeRules) {
    MapSettings settings = new MapSettings(Map.of());
    SensorOptimizer underTest = new SensorOptimizer(fs, activeRules, settings.asConfig());
    return underTest;
  }

}
