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
package org.sonarsource.sonarlint.core.analysis.container.analysis.sensor;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.InputFileIndex;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintFileSystem;
import org.sonarsource.sonarlint.core.analysis.sonarapi.ActiveRuleAdapter;
import org.sonarsource.sonarlint.core.analysis.sonarapi.ActiveRulesAdapter;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultSensorDescriptor;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MapSettings;
import org.sonarsource.sonarlint.core.commons.Language;
import testutils.TestInputFileBuilder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensorOptimizerTests {

  private FileSystem fs;
  private SensorOptimizer optimizer;
  private MapSettings settings;
  private final InputFileIndex inputFileCache = new InputFileIndex();

  @BeforeEach
  void prepare() throws Exception {
    fs = new SonarLintFileSystem(mock(AnalysisConfiguration.class), inputFileCache);
    settings = new MapSettings(Map.of());
    optimizer = new SensorOptimizer(fs, mock(ActiveRules.class), settings.asConfig());
  }

  @Test
  void should_run_analyzer_with_no_metadata() {
    var descriptor = new DefaultSensorDescriptor();

    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

  @Test
  void should_optimize_on_language() {
    var descriptor = new DefaultSensorDescriptor()
      .onlyOnLanguages("java", "php");
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.java").setLanguage(Language.JAVA).build());
    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

  @Test
  void should_optimize_on_type() {
    var descriptor = new DefaultSensorDescriptor()
      .onlyOnFileType(InputFile.Type.MAIN);
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("tests/FooTest.java").setType(InputFile.Type.TEST).build());
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.java").setType(InputFile.Type.MAIN).build());
    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

  @Test
  void should_optimize_on_both_type_and_language() {
    var descriptor = new DefaultSensorDescriptor()
      .onlyOnLanguages("java", "php")
      .onlyOnFileType(InputFile.Type.MAIN);
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("tests/FooTest.java").setLanguage(Language.JAVA).setType(InputFile.Type.TEST).build());
    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.cbl").setLanguage(Language.COBOL).setType(InputFile.Type.MAIN).build());
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.java").setLanguage(Language.JAVA).setType(InputFile.Type.MAIN).build());
    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

  @Test
  void should_optimize_on_repository() {
    var descriptor = new DefaultSensorDescriptor()
      .createIssuesForRuleRepositories("squid");
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    var ruleAnotherRepo = mock(ActiveRuleAdapter.class);
    when(ruleAnotherRepo.ruleKey()).thenReturn(RuleKey.of("repo1", "foo"));
    ActiveRules activeRules = new ActiveRulesAdapter(List.of(ruleAnotherRepo));
    optimizer = new SensorOptimizer(fs, activeRules, settings.asConfig());

    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    var ruleSquid = mock(ActiveRuleAdapter.class);
    when(ruleSquid.ruleKey()).thenReturn(RuleKey.of("squid", "rule"));

    activeRules = new ActiveRulesAdapter(asList(ruleSquid, ruleAnotherRepo));

    optimizer = new SensorOptimizer(fs, activeRules, settings.asConfig());

    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

  @Test
  void should_optimize_on_settings() {
    var descriptor = new DefaultSensorDescriptor().onlyWhenConfiguration(c -> c.hasKey("sonar.foo.reportPath"));
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    settings = new MapSettings(Map.of("sonar.foo.reportPath", "foo"));
    optimizer = new SensorOptimizer(fs, mock(ActiveRules.class), settings.asConfig());
    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

}
