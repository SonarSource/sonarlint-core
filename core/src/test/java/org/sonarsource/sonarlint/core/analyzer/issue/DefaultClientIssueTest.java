/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.analyzer.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.fs.internal.DefaultTextPointer;
import org.sonar.api.batch.fs.internal.DefaultTextRange;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.sensor.issue.Issue.Flow;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class DefaultClientIssueTest {
  @Mock
  private ActiveRule activeRule;
  @Mock
  private Rule rule;
  @Mock
  private TextRange textRange;
  @Mock
  private ClientInputFile clientInputFile;

  private DefaultClientIssue issue;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void transformIssue() {
    textRange = new DefaultTextRange(new DefaultTextPointer(1, 2), new DefaultTextPointer(2, 3));
    IssueLocation location1 = mock(IssueLocation.class);

    when(location1.textRange()).thenReturn(new DefaultTextRange(new DefaultTextPointer(4, 4), new DefaultTextPointer(5, 5)));
    when(location1.message()).thenReturn("location1");
    IssueLocation location2 = mock(IssueLocation.class);

    when(location2.textRange()).thenReturn(new DefaultTextRange(new DefaultTextPointer(6, 6), new DefaultTextPointer(7, 7)));
    when(location2.message()).thenReturn("location2");

    when(rule.name()).thenReturn("name");

    Flow flow1 = mock(Flow.class);
    when(flow1.locations()).thenReturn(Collections.singletonList(location1));

    Flow flow2 = mock(Flow.class);
    when(flow2.locations()).thenReturn(Arrays.asList(location1, location2));

    issue = new DefaultClientIssue("MAJOR", activeRule, rule, "msg", textRange, clientInputFile, Arrays.asList(flow1, flow2));

    assertThat(issue.getStartLine()).isEqualTo(1);
    assertThat(issue.getStartLineOffset()).isEqualTo(2);
    assertThat(issue.getEndLine()).isEqualTo(2);
    assertThat(issue.getEndLineOffset()).isEqualTo(3);

    assertThat(issue.getMessage()).isEqualTo("msg");
    assertThat(issue.getSeverity()).isEqualTo("MAJOR");
    assertThat(issue.getInputFile()).isEqualTo(clientInputFile);

    assertThat(issue.getRuleName()).isEqualTo("name");

    assertThat(issue.flows()).hasSize(2);
    assertThat(issue.flows().get(0).locations()).hasSize(1);
    assertThat(issue.flows().get(0).locations().get(0)).extracting("startLine", "startLineOffset", "endLine", "endLineOffset", "message")
      .containsExactly(4, 4, 5, 5, "location1");

    assertThat(issue.flows().get(1).locations()).hasSize(2);
    assertThat(issue.flows().get(1).locations().get(0)).extracting("startLine", "startLineOffset", "endLine", "endLineOffset", "message")
      .containsExactly(4, 4, 5, 5, "location1");
    assertThat(issue.flows().get(1).locations().get(1)).extracting("startLine", "startLineOffset", "endLine", "endLineOffset", "message")
      .containsExactly(6, 6, 7, 7, "location2");
  }

  @Test
  public void nullRange() {
    issue = new DefaultClientIssue("MAJOR", activeRule, rule, "msg", null, null, Collections.emptyList());

    assertThat(issue.getStartLine()).isNull();
    assertThat(issue.getStartLineOffset()).isNull();
    assertThat(issue.getEndLine()).isNull();
    assertThat(issue.getEndLineOffset()).isNull();

    assertThat(issue.flows()).isEmpty();
  }
}
