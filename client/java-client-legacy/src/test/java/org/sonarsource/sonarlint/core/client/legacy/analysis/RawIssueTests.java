/*
 * SonarLint Core - Java Client Legacy
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
package org.sonarsource.sonarlint.core.client.legacy.analysis;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.batch.fs.InputComponent;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RawIssueTests {
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

    var rule = new GetRuleDetailsResponse(IssueSeverity.MAJOR, RuleType.BUG, CleanCodeAttribute.CLEAR, List.of(new ImpactDto(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW)),
      null);

    var overriddenImpacts = Map.of(org.sonarsource.sonarlint.core.commons.SoftwareQuality.MAINTAINABILITY, org.sonarsource.sonarlint.core.commons.ImpactSeverity.MEDIUM);
    var issue = new Issue("rule:S123", "msg", overriddenImpacts, textRange, clientInputFile, null, null, Optional.empty());

    var underTest = new RawIssue(issue, rule);

    assertThat(underTest.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(underTest.getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(underTest.getTextRange().getEndLine()).isEqualTo(2);
    assertThat(underTest.getTextRange().getEndLineOffset()).isEqualTo(3);

    assertThat(underTest.getMessage()).isEqualTo("msg");
    assertThat(underTest.getSeverity()).isEqualTo(IssueSeverity.MAJOR);
    assertThat(underTest.getType()).isEqualTo(RuleType.BUG);
    assertThat(underTest.getCleanCodeAttribute()).hasValue(CleanCodeAttribute.CLEAR);
    assertThat(underTest.getImpacts()).containsExactly(entry(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.MEDIUM));
    assertThat(underTest.getInputFile()).isEqualTo(clientInputFile);
    assertThat(underTest.getVulnerabilityProbability()).isEmpty();
  }

  @Test
  void it_should_generate_a_readable_toString() {
    var rawIssue = new RawIssue(new Issue("rule:S123", "msg", Map.of(), textRange, clientInputFile, null, null, Optional.empty()),
      new GetRuleDetailsResponse(IssueSeverity.MAJOR, RuleType.BUG, CleanCodeAttribute.CLEAR, List.of(new ImpactDto(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW)),
        null));

    var string = rawIssue.toString();

    assertThat(string).isEqualTo("[rule=rule:S123, severity=MAJOR, range={ startLine=0, startOffset=0, endLine=0, endOffset=0 }, file=null]");
  }

}
