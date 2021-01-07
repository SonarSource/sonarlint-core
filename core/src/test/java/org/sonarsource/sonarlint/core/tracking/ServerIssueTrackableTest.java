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

import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerIssueTrackableTest {

  private final ServerIssue serverIssue = mock(ServerIssue.class);
  private final Trackable trackable = new ServerIssueTrackable(serverIssue);

  public ServerIssueTrackableTest() {
    when(serverIssue.checksum()).thenReturn("blah");
    when(serverIssue.resolution()).thenReturn("non-empty");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void should_not_have_issue() {
    trackable.getIssue();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void should_not_have_ruleName() {
    trackable.getRuleName();
  }

  @Test
  public void should_delegate_fields_to_protobuf_issue() {
    assertThat(trackable.getMessage()).isEqualTo(serverIssue.message());
    assertThat(trackable.getLineHash()).isEqualTo(serverIssue.checksum().hashCode());
    assertThat(trackable.getRuleKey()).isEqualTo(serverIssue.ruleKey());
    assertThat(trackable.isResolved()).isEqualTo(!serverIssue.resolution().isEmpty());
    assertThat(trackable.getAssignee()).isEqualTo(serverIssue.assigneeLogin());
    assertThat(trackable.getSeverity()).isEqualTo(serverIssue.severity());
    assertThat(trackable.getTextRange().getStartLine()).isEqualTo(serverIssue.line());
  }
}
