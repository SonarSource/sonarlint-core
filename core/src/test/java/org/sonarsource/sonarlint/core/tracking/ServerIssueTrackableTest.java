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
package org.sonarsource.sonarlint.core.tracking;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.analysis.api.TextRange;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerIssueTrackableTest {

  private final ServerIssue serverIssue = mock(ServerIssue.class);
  private Trackable trackable;

  @Before
  public void prepare() {
    when(serverIssue.lineHash()).thenReturn("blah");
    when(serverIssue.resolution()).thenReturn("non-empty");
    TextRange serverTextRange = new TextRange(22);
    when(serverIssue.getTextRange()).thenReturn(serverTextRange);
    trackable = new ServerIssueTrackable(serverIssue);
  }

  @Test
  public void should_delegate_fields_to_server_issue() {
    assertThat(trackable.getMessage()).isEqualTo(serverIssue.getMessage());
    assertThat(trackable.getLineHash()).isEqualTo(serverIssue.lineHash().hashCode());
    assertThat(trackable.getRuleKey()).isEqualTo(serverIssue.ruleKey());
    assertThat(trackable.isResolved()).isEqualTo(!serverIssue.resolution().isEmpty());
    assertThat(trackable.getAssignee()).isEqualTo(serverIssue.assigneeLogin());
    assertThat(trackable.getSeverity()).isEqualTo(serverIssue.severity());
    assertThat(trackable.getTextRange().getStartLine()).isEqualTo(serverIssue.getTextRange().getStartLine());
  }
}
