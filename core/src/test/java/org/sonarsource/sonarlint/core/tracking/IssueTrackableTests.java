/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.tracking.DigestUtils.digest;

class IssueTrackableTests {

  private final Issue issue = mock(Issue.class);

  @Test
  void should_have_null_content_hashes_when_constructed_without_content_info() {
    var trackable = new IssueTrackable(issue);
    assertThat(trackable.getTextRange()).isNull();
    assertThat(trackable.getTextRangeHash()).isNull();
    assertThat(trackable.getLineHash()).isNull();
  }

  @Test
  void should_have_null_content_hashes_when_constructed_without_null_content_info() {
    var trackable = new IssueTrackable(issue, null, null, null);
    assertThat(trackable.getTextRange()).isNull();
    assertThat(trackable.getTextRangeHash()).isNull();
    assertThat(trackable.getLineHash()).isNull();
  }

  @Test
  void should_have_non_null_hashes_when_constructed_with_non_null_content_info() {
    var textRangeContent = "text range content";
    var lineContent = "line content";
    var trackable = new IssueTrackable(issue, null, textRangeContent, lineContent);
    assertThat(trackable.getTextRangeHash()).isEqualTo(hash(textRangeContent));
    assertThat(trackable.getLineHash()).isEqualTo(hash(lineContent));
  }

  private int hash(String content) {
    return digest(content).hashCode();
  }

  @Test
  void should_delegate_fields_to_issue() {
    var severity = "dummy severity";
    when(issue.getSeverity()).thenReturn(severity);

    var trackable = new IssueTrackable(issue, null, null, null);
    assertThat(trackable.getClientObject()).isEqualTo(issue);
    assertThat(trackable.getUserSeverity()).isEqualTo(severity);
  }
}
