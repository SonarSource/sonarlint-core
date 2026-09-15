/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.IssueResolution;
import org.sonar.api.rule.RuleKey;
import testutils.TestInputFileBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultSonarLintIssueResolutionTests {

  private InputFile inputFile;
  private SensorStorage storage;

  @BeforeEach
  void setUp() {
    inputFile = new TestInputFileBuilder("src/Foo.java")
      .initMetadata("Foo\nBar\n")
      .build();
    storage = mock(SensorStorage.class);
  }

  @Test
  void build_and_save_issue_resolution() {
    var range = inputFile.selectLine(1);
    var ruleKey = RuleKey.of("repo", "rule");
    var resolution = new DefaultSonarLintIssueResolution(storage)
      .on(inputFile)
      .at(range)
      .status(IssueResolution.Status.FALSE_POSITIVE)
      .forRules(List.of(ruleKey))
      .comment("justified");

    assertThat(resolution.inputFile()).isEqualTo(inputFile);
    assertThat(resolution.textRange()).isEqualTo(range);
    assertThat(resolution.status()).isEqualTo(IssueResolution.Status.FALSE_POSITIVE);
    assertThat(resolution.ruleKeys()).containsExactly(ruleKey).isUnmodifiable();
    assertThat(resolution.comment()).isEqualTo("justified");

    resolution.save();

    verify(storage).store(resolution);
  }

  @Test
  void defaults_status_to_default() {
    var resolution = new DefaultSonarLintIssueResolution(storage)
      .on(inputFile)
      .at(inputFile.selectLine(1))
      .forRules(List.of(RuleKey.of("repo", "rule")))
      .comment("justified");

    assertThat(resolution.status()).isEqualTo(IssueResolution.Status.DEFAULT);
  }

  @Test
  void fails_when_mandatory_fields_are_missing() {
    var missingFile = new DefaultSonarLintIssueResolution(storage);
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(missingFile::save);

    var missingRange = new DefaultSonarLintIssueResolution(storage).on(inputFile);
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(missingRange::save);

    var missingComment = new DefaultSonarLintIssueResolution(storage)
      .on(inputFile)
      .at(inputFile.selectLine(1));
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(missingComment::save);

    var missingRuleKeys = new DefaultSonarLintIssueResolution(storage)
      .on(inputFile)
      .at(inputFile.selectLine(1))
      .comment("justified");
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(missingRuleKeys::save);
  }

  @Test
  void fails_when_on_or_at_called_twice() {
    var range = inputFile.selectLine(1);

    var alreadyOnFile = new DefaultSonarLintIssueResolution(storage).on(inputFile);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> alreadyOnFile.on(inputFile));

    var alreadyAtRange = new DefaultSonarLintIssueResolution(storage).at(range);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> alreadyAtRange.at(range));
  }
}
