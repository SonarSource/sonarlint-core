/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.common.analysis;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.batch.fs.InputComponent;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultClientIssueTests {
  @Mock
  private SonarLintRuleDefinition rule;
  @Mock
  private TextRange textRange;
  @Mock
  private ClientInputFile clientInputFile;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  void transformIssue() {
    var currentFile = mock(InputComponent.class);
    var currentFileKey = "currentFileKey";
    when(currentFile.key()).thenReturn(currentFileKey);
    var anotherFile = mock(InputComponent.class);
    when(anotherFile.key()).thenReturn("anotherFileKey");

    textRange = new TextRange(1, 2, 2, 3);

    when(rule.getName()).thenReturn("name");
    when(rule.getType()).thenReturn(RuleType.BUG);
    when(rule.getDefaultSeverity()).thenReturn(IssueSeverity.MAJOR);

    var issue = new Issue("rule:S123", "msg", textRange, clientInputFile, null, null, Optional.empty());

    var underTest = new DefaultClientIssue(issue, rule);

    assertThat(underTest.getStartLine()).isEqualTo(1);
    assertThat(underTest.getStartLineOffset()).isEqualTo(2);
    assertThat(underTest.getEndLine()).isEqualTo(2);
    assertThat(underTest.getEndLineOffset()).isEqualTo(3);

    assertThat(underTest.getMessage()).isEqualTo("msg");
    assertThat(underTest.getSeverity()).isEqualTo(IssueSeverity.MAJOR);
    assertThat(underTest.getType()).isEqualTo(RuleType.BUG);
    assertThat(underTest.getInputFile()).isEqualTo(clientInputFile);
    assertThat(underTest.getVulnerabilityProbability()).isEmpty();
  }

}
