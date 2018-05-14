/*
 * SonarLint Language Server
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarlint.languageserver;

import java.util.Collections;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DelegatingIssueTest {

  private final Issue issue;
  private final DelegatingIssue delegatingIssue;

  public DelegatingIssueTest() {
    issue = mock(Issue.class);
    when(issue.getSeverity()).thenReturn("BLOCKER");
    when(issue.getType()).thenReturn("BUG");
    when(issue.getMessage()).thenReturn("don't do this");
    when(issue.getRuleKey()).thenReturn("squid:123");
    when(issue.getRuleName()).thenReturn("don't do that");
    when(issue.getStartLine()).thenReturn(2);
    when(issue.getStartLineOffset()).thenReturn(3);
    when(issue.getEndLine()).thenReturn(4);
    when(issue.getEndLineOffset()).thenReturn(5);
    when(issue.flows()).thenReturn(Collections.singletonList(mock(Issue.Flow.class)));
    when(issue.getInputFile()).thenReturn(mock(ClientInputFile.class));

    delegatingIssue = new DelegatingIssue(issue);
  }

  @Test
  public void testGetSeverity() {
    assertThat(delegatingIssue.getSeverity()).isNotEmpty().isEqualTo(issue.getSeverity());
  }

  @Test
  public void testGetType() {
    assertThat(delegatingIssue.getType()).isNotEmpty().isEqualTo(issue.getType());
  }

  @Test
  public void testGetMessage() {
    assertThat(delegatingIssue.getMessage()).isNotEmpty().isEqualTo(issue.getMessage());
  }

  @Test
  public void testGetRuleKey() {
    assertThat(delegatingIssue.getRuleKey()).isNotEmpty().isEqualTo(issue.getRuleKey());
  }

  @Test
  public void testGetRuleName() {
    assertThat(delegatingIssue.getRuleName()).isNotEmpty().isEqualTo(issue.getRuleName());
  }

  @Test
  public void testGetStartLine() {
    assertThat(delegatingIssue.getStartLine()).isGreaterThan(0).isEqualTo(issue.getStartLine());
  }

  @Test
  public void testGetStartLineOffset() {
    assertThat(delegatingIssue.getStartLineOffset()).isGreaterThan(0).isEqualTo(issue.getStartLineOffset());
  }

  @Test
  public void testGetEndLine() {
    assertThat(delegatingIssue.getEndLine()).isGreaterThan(0).isEqualTo(issue.getEndLine());
  }

  @Test
  public void testGetEndLineOffset() {
    assertThat(delegatingIssue.getEndLineOffset()).isGreaterThan(0).isEqualTo(issue.getEndLineOffset());
  }

  @Test
  public void testFlows() {
    assertThat(delegatingIssue.flows()).isNotEmpty().isEqualTo(issue.flows());
  }

  @Test
  public void testGetInputFile() {
    assertThat(delegatingIssue.getInputFile()).isNotNull().isEqualTo(issue.getInputFile());
  }
}
