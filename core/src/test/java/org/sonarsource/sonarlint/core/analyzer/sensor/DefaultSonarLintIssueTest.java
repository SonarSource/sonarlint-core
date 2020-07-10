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

import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.TestInputFileBuilder;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputDir;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputProject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DefaultSonarLintIssueTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private SonarLintInputProject project;
  private Path baseDir;

  private InputFile inputFile = new TestInputFileBuilder("src/Foo.php")
    .initMetadata("Foo\nBar\n")
    .build();

  @Before
  public void prepare() throws IOException {
    project = new SonarLintInputProject();
    baseDir = temp.newFolder().toPath();
  }

  @Test
  public void build_file_issue() {
    SensorStorage storage = mock(SensorStorage.class);
    DefaultSonarLintIssue issue = new DefaultSonarLintIssue(project, baseDir, storage)
      .at(new DefaultSonarLintIssueLocation()
        .on(inputFile)
        .at(inputFile.selectLine(1))
        .message("Wrong way!"))
      .forRule(RuleKey.of("repo", "rule"))
      .gap(10.0);

    assertThat(issue.primaryLocation().inputComponent()).isEqualTo(inputFile);
    assertThat(issue.ruleKey()).isEqualTo(RuleKey.of("repo", "rule"));
    assertThat(issue.primaryLocation().textRange().start().line()).isEqualTo(1);
    assertThat(issue.primaryLocation().message()).isEqualTo("Wrong way!");

    assertThatExceptionOfType(UnsupportedOperationException.class)
      .isThrownBy(() -> issue.gap())
      .withMessage("No gap in SonarLint");

    issue.save();

    verify(storage).store(issue);
  }

  @Test
  public void move_directory_issue_to_project_root() {
    SensorStorage storage = mock(SensorStorage.class);
    DefaultSonarLintIssue issue = new DefaultSonarLintIssue(project, baseDir, storage)
      .at(new DefaultSonarLintIssueLocation()
        .on(new SonarLintInputDir(baseDir.resolve("src/main")))
        .message("Wrong way!"))
      .forRule(RuleKey.of("repo", "rule"))
      .overrideSeverity(Severity.BLOCKER);

    assertThat(issue.primaryLocation().inputComponent()).isEqualTo(project);
    assertThat(issue.ruleKey()).isEqualTo(RuleKey.of("repo", "rule"));
    assertThat(issue.primaryLocation().textRange()).isNull();
    assertThat(issue.primaryLocation().message()).isEqualTo("[src/main] Wrong way!");
    assertThat(issue.overriddenSeverity()).isEqualTo(Severity.BLOCKER);

    issue.save();

    verify(storage).store(issue);
  }

  @Test
  public void build_project_issue() throws IOException {
    SensorStorage storage = mock(SensorStorage.class);
    DefaultSonarLintIssue issue = new DefaultSonarLintIssue(project, baseDir, storage)
      .at(new DefaultSonarLintIssueLocation()
        .on(project)
        .message("Wrong way!"))
      .forRule(RuleKey.of("repo", "rule"))
      .gap(10.0);

    assertThat(issue.primaryLocation().inputComponent()).isEqualTo(project);
    assertThat(issue.ruleKey()).isEqualTo(RuleKey.of("repo", "rule"));
    assertThat(issue.primaryLocation().textRange()).isNull();
    assertThat(issue.primaryLocation().message()).isEqualTo("Wrong way!");

    issue.save();

    verify(storage).store(issue);
  }

}
