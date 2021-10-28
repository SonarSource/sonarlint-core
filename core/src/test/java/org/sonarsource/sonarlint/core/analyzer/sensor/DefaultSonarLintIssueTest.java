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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.TestInputFileBuilder;
import org.sonarsource.sonarlint.core.client.api.common.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.client.api.common.QuickFix;
import org.sonarsource.sonarlint.core.client.api.common.QuickFixable;
import org.sonarsource.sonarlint.core.client.api.common.TextEdit;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputDir;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputProject;
import org.sonarsource.sonarlint.plugin.api.issue.NewInputFileEdit;
import org.sonarsource.sonarlint.plugin.api.issue.NewQuickFix;

import static org.apache.commons.lang.StringUtils.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DefaultSonarLintIssueTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private SonarLintInputProject project;
  private Path baseDir;

  private final InputFile inputFile = new TestInputFileBuilder("src/Foo.php")
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
    TextRange range = inputFile.selectLine(1);
    DefaultSonarLintIssue issue = new DefaultSonarLintIssue(project, baseDir, storage)
      .at(new DefaultSonarLintIssueLocation()
        .on(inputFile)
        .at(range)
        .message("Wrong way!"))
      .forRule(RuleKey.of("repo", "rule"))
      .gap(10.0);

    assertThat(issue.primaryLocation().inputComponent()).isEqualTo(inputFile);
    assertThat(issue.ruleKey()).isEqualTo(RuleKey.of("repo", "rule"));
    assertThat(issue.primaryLocation().textRange().loadPluginMetadata().line()).isEqualTo(1);
    assertThat(issue.primaryLocation().message()).isEqualTo("Wrong way!");

    assertThatExceptionOfType(UnsupportedOperationException.class)
      .isThrownBy(() -> issue.gap())
      .withMessage("No gap in SonarLint");

    NewQuickFix newQuickFix = issue.newQuickFix().message("Fix this issue");
    NewInputFileEdit newInputFileEdit = newQuickFix.newInputFileEdit().on(inputFile);
    newInputFileEdit.addTextEdit(newInputFileEdit.newTextEdit().at(range).withNewText("// Fixed!"));
    newQuickFix.addInputFileEdit(newInputFileEdit);
    issue.addQuickFix(newQuickFix);

    List<QuickFix> quickFixes = ((QuickFixable) issue).quickFixes();
    assertThat(quickFixes).hasSize(1);
    QuickFix quickFix = quickFixes.get(0);
    assertThat(quickFix.message()).isEqualTo("Fix this issue");
    List<ClientInputFileEdit> inputFileEdits = quickFix.inputFileEdits();
    assertThat(inputFileEdits).hasSize(1);
    ClientInputFileEdit inputFileEdit = inputFileEdits.get(0);
    assertThat(inputFileEdit.target()).isEqualTo(((SonarLintInputFile) inputFile).getClientInputFile());
    assertThat(inputFileEdit.textEdits()).hasSize(1);
    TextEdit textEdit = inputFileEdit.textEdits().get(0);
    assertThat(textEdit.range().getStartLine()).isEqualTo(range.loadPluginMetadata().line());
    assertThat(textEdit.range().getStartLineOffset()).isEqualTo(range.loadPluginMetadata().lineOffset());
    assertThat(textEdit.range().getEndLine()).isEqualTo(range.end().line());
    assertThat(textEdit.range().getEndLineOffset()).isEqualTo(range.end().lineOffset());
    assertThat(textEdit.newText()).isEqualTo("// Fixed!");

    issue.save();

    verify(storage).store(issue);
  }

  @Test
  public void replace_null_characters() {
    SensorStorage storage = mock(SensorStorage.class);
    DefaultSonarLintIssue issue = new DefaultSonarLintIssue(project, baseDir, storage)
      .at(new DefaultSonarLintIssueLocation()
        .on(inputFile)
        .message("Wrong \u0000 use of NULL\u0000"))
      .forRule(RuleKey.of("repo", "rule"));

    assertThat(issue.primaryLocation().message()).isEqualTo("Wrong [NULL] use of NULL[NULL]");

    issue.save();

    verify(storage).store(issue);
  }

  @Test
  public void truncate_and_trim() {
    SensorStorage storage = mock(SensorStorage.class);
    String prefix = "prefix: ";
    DefaultSonarLintIssue issue = new DefaultSonarLintIssue(project, baseDir, storage)
      .at(new DefaultSonarLintIssueLocation()
        .on(inputFile)
        .message("   " + prefix + repeat("a", 4000)))
      .forRule(RuleKey.of("repo", "rule"));

    String ellipse = "...";
    assertThat(issue.primaryLocation().message()).isEqualTo(prefix + repeat("a", 4000 - prefix.length() - ellipse.length()) + ellipse);

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
