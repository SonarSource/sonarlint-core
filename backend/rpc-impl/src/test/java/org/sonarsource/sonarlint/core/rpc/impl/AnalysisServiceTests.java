/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonarsource.sonarlint.core.analysis.RawIssue;
import org.sonarsource.sonarlint.core.analysis.RuleDetailsForAnalysis;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextPointer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextRange;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.sonarapi.ActiveRuleAdapter;
import org.sonarsource.sonarlint.core.commons.api.TextRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisServiceTests {

  @Test
  void it_should_convert_issue_flaws_and_quick_fixes_to_raw_issue_dto() throws IOException {
    var issueLocation = mock(IssueLocation.class);
    var inputComponent = mock(SonarLintInputFile.class);
    when(inputComponent.isFile()).thenReturn(true);
    when(inputComponent.key()).thenReturn("inputComponentKey");
    when(issueLocation.message()).thenReturn("issue location message");
    when(issueLocation.textRange()).thenReturn(new DefaultTextRange(new DefaultTextPointer(1, 2),
      new DefaultTextPointer(3, 4)));
    when(issueLocation.inputComponent()).thenReturn(inputComponent);
    var clientInputFile = mock(ClientInputFile.class);
    when(clientInputFile.contents()).thenReturn("content");

    var issue = new Issue(new ActiveRuleAdapter(new ActiveRule("repo:ruleKey", "languageKey")),
      "primary message", Map.of(),
      new DefaultTextRange(new DefaultTextPointer(1, 1), new DefaultTextPointer(1, 1)),
      clientInputFile, List.of(new Flow(List.of(issueLocation))), List.of(new QuickFix(List.of(
      new ClientInputFileEdit(clientInputFile, List.of(new TextEdit(
        new TextRange(5, 6, 7, 8), "Quick fix text")))),
      "Quick fix message")), Optional.of(""));
    var ruleDetailsResponse = new RuleDetailsForAnalysis(org.sonarsource.sonarlint.core.commons.IssueSeverity.BLOCKER,
      org.sonarsource.sonarlint.core.commons.RuleType.BUG, org.sonarsource.sonarlint.core.commons.CleanCodeAttribute.CLEAR,
      Map.of(), org.sonarsource.sonarlint.core.commons.VulnerabilityProbability.HIGH);

    var rawIssueDto = AnalysisRpcServiceDelegate.toDto(new RawIssue(issue, ruleDetailsResponse));

    assertThat(rawIssueDto.getRuleKey()).isEqualTo("repo:ruleKey");
    var rawIssueLocationDto = rawIssueDto.getFlows().get(0).getLocations().get(0);
    assertThat(rawIssueLocationDto.getMessage()).isEqualTo("issue location message");
    var issueLocationTextRange = rawIssueLocationDto.getTextRange();
    assertThat(issueLocationTextRange).isNotNull();
    assertThat(issueLocationTextRange.getStartLine()).isEqualTo(1);
    assertThat(issueLocationTextRange.getStartLineOffset()).isEqualTo(2);
    assertThat(issueLocationTextRange.getEndLine()).isEqualTo(3);
    assertThat(issueLocationTextRange.getEndLineOffset()).isEqualTo(4);
    var quickFix = rawIssueDto.getQuickFixes().get(0);
    assertThat(quickFix).isNotNull();
    var fileEdit = quickFix.fileEdits().get(0);
    assertThat(fileEdit).isNotNull();
    var textEdit = fileEdit.textEdits().get(0);
    assertThat(textEdit).isNotNull();
    var textRange = textEdit.range();
    assertThat(textRange).isNotNull();
    assertThat(textRange.getStartLine()).isEqualTo(5);
    assertThat(textRange.getStartLineOffset()).isEqualTo(6);
    assertThat(textRange.getEndLine()).isEqualTo(7);
    assertThat(textRange.getEndLineOffset()).isEqualTo(8);
  }

}
