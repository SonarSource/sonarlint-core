/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.TestInputFileBuilder;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.InputFileCache;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintFileSystem;
import org.sonarsource.sonarlint.core.container.global.DefaultActiveRules;
import org.sonarsource.sonarlint.core.container.global.MapSettings;

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
  private SensorOptimizer optimizer;
  private MapSettings settings;
  private InputFileCache inputFileCache = new InputFileCache();

  @Before
  public void prepare() throws Exception {
    fs = new SonarLintFileSystem(mock(AbstractAnalysisConfiguration.class), inputFileCache);
    settings = new MapSettings();
    optimizer = new SensorOptimizer(fs, mock(ActiveRules.class), settings.asConfig());
  }

  @Test
  public void should_run_analyzer_with_no_metadata() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();

    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

  @Test
  public void should_optimize_on_language() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
      .onlyOnLanguages("java", "php");
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.java").setLanguage(Language.JAVA).build());
    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

  @Test
  public void should_optimize_on_type() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
      .onlyOnFileType(InputFile.Type.MAIN);
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("tests/FooTest.java").setType(InputFile.Type.TEST).build());
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.java").setType(InputFile.Type.MAIN).build());
    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

  @Test
  public void should_optimize_on_both_type_and_language() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
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
  public void should_optimize_on_repository() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
      .createIssuesForRuleRepositories("squid");
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    ActiveRule ruleAnotherRepo = mock(ActiveRule.class);
    when(ruleAnotherRepo.ruleKey()).thenReturn(RuleKey.of("repo1", "foo"));
    ActiveRules activeRules = new DefaultActiveRules(asList(ruleAnotherRepo));
    optimizer = new SensorOptimizer(fs, activeRules, settings.asConfig());

    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    ActiveRule ruleSquid = mock(ActiveRule.class);
    when(ruleSquid.ruleKey()).thenReturn(RuleKey.of("squid", "rule"));

    activeRules = new DefaultActiveRules(asList(ruleSquid, ruleAnotherRepo));

    optimizer = new SensorOptimizer(fs, activeRules, settings.asConfig());

    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

  @Test
  public void should_optimize_on_settings() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor()
      .requireProperty("sonar.foo.reportPath");
    assertThat(optimizer.shouldExecute(descriptor)).isFalse();

    settings.setProperty("sonar.foo.reportPath", "foo");
    assertThat(optimizer.shouldExecute(descriptor)).isTrue();
  }

}
