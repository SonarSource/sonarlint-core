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
package org.sonarsource.sonarlint.core.tracking;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssueResolution;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalOnlyIssueTrackableTests {

  private final LocalOnlyIssue localOnlyIssue = mock(LocalOnlyIssue.class);
  private Trackable trackable;

  @BeforeEach
  void prepare() {
    when(localOnlyIssue.getId()).thenReturn(UUID.randomUUID());
    when(localOnlyIssue.getMessage()).thenReturn("msg");
    when(localOnlyIssue.getResolution()).thenReturn(new LocalOnlyIssueResolution(IssueStatus.WONT_FIX, Instant.now(), null));
    when(localOnlyIssue.getRuleKey()).thenReturn("ruleKey");
    when(localOnlyIssue.getServerRelativePath()).thenReturn("file/path");
    when(localOnlyIssue.getTextRangeWithHash()).thenReturn(new TextRangeWithHash(1, 2, 3, 4, "hash"));
    when(localOnlyIssue.getLineWithHash()).thenReturn(new LineWithHash(1, "hash"));
    trackable = new LocalOnlyIssueTrackable(localOnlyIssue);
  }

  @Test
  void should_delegate_fields_to_server_issue() {
    assertThat(trackable.getMessage()).isEqualTo(localOnlyIssue.getMessage());
    assertThat(trackable.isResolved()).isEqualTo(localOnlyIssue.getResolution() != null);
    assertThat(trackable.getRuleKey()).isEqualTo(localOnlyIssue.getRuleKey());
    assertThat(trackable.getLine()).isEqualTo(localOnlyIssue.getLineWithHash().getNumber());
    assertThat(trackable.getLineHash()).isEqualTo(localOnlyIssue.getLineWithHash().getHash());
    assertThat(trackable.getTextRange()).isNotNull();
    assertThat(trackable.getServerIssueKey()).isNull();
    assertThat(trackable.getCreationDate()).isNull();
    assertThat(trackable.getSeverity()).isNull();
    assertThat(trackable.getType()).isNull();
    assertThat(trackable.getReviewStatus()).isNull();
  }

}
