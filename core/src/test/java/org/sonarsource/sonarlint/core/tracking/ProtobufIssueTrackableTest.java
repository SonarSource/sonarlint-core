/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
import org.sonarsource.sonarlint.core.proto.Sonarlint;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufIssueTrackableTest {

  private final Trackable empty = new ProtobufIssueTrackable(Sonarlint.Issues.Issue.newBuilder().build());

  private final Sonarlint.Issues.Issue completeIssue = Sonarlint.Issues.Issue.newBuilder()
    .setMessage("message")
    .setChecksum(7)
    .setRuleKey("rule key")
    .setResolved(true)
    .setAssignee("user")
    .build();

  private final Trackable completeTrackable = new ProtobufIssueTrackable(completeIssue);

  @Test
  public void should_return_null_serverIssueKey_when_unset() {
    assertThat(empty.getServerIssueKey()).isNull();
  }

  @Test
  public void should_return_null_line_when_unset() {
    assertThat(empty.getLine()).isNull();
  }

  @Test
  public void should_return_null_creationDate_when_unset() {
    assertThat(empty.getCreationDate()).isNull();
  }

  @Test
  public void should_have_null_textRangeHash() {
    assertThat(completeTrackable.getTextRangeHash()).isNull();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void should_not_have_issue() {
    completeTrackable.getIssue();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void should_not_have_ruleName() {
    completeTrackable.getRuleName();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void should_not_have_severity() {
    completeTrackable.getSeverity();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void should_not_have_textRange() {
    completeTrackable.getTextRange();
  }

  @Test
  public void should_delegate_fields_to_protobuf_issue() {
    assertThat(completeTrackable.getMessage()).isEqualTo(completeIssue.getMessage());
    assertThat(completeTrackable.getLineHash()).isEqualTo(completeIssue.getChecksum());
    assertThat(completeTrackable.getRuleKey()).isEqualTo(completeIssue.getRuleKey());
    assertThat(completeTrackable.isResolved()).isEqualTo(completeIssue.getResolved());
    assertThat(completeTrackable.getAssignee()).isEqualTo(completeIssue.getAssignee());
  }
}
