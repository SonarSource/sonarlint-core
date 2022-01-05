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
package org.sonarsource.sonarlint.core.container.model;

import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DefaultServerIssueTests {
  @Test
  void testRoundTrips() {
    var issue = new DefaultServerIssue();
    var i1 = Instant.ofEpochMilli(100_000_000);
    assertThat(issue.setLineHash("checksum1").lineHash()).isEqualTo("checksum1");
    assertThat(issue.setCreationDate(i1).creationDate()).isEqualTo(i1);
    assertThat(issue.setAssigneeLogin("login1").assigneeLogin()).isEqualTo("login1");
    assertThat(issue.setFilePath("path1").getFilePath()).isEqualTo("path1");
    assertThat(issue.setKey("key1").key()).isEqualTo("key1");
    issue.setTextRange(new org.sonarsource.sonarlint.core.analysis.api.TextRange(1,
      2,
      3,
      4));
    assertThat(issue.getStartLine()).isEqualTo(1);
    assertThat(issue.getStartLineOffset()).isEqualTo(2);
    assertThat(issue.getEndLine()).isEqualTo(3);
    assertThat(issue.getEndLineOffset()).isEqualTo(4);
    assertThat(issue.setSeverity("MAJOR").severity()).isEqualTo("MAJOR");
    assertThat(issue.setRuleKey("rule1").ruleKey()).isEqualTo("rule1");
    assertThat(issue.setResolution("RESOLVED").resolution()).isEqualTo("RESOLVED");
    assertThat(issue.setMessage("msg1").getMessage()).isEqualTo("msg1");
    assertThat(issue.setType("type").type()).isEqualTo("type");

    assertThat(issue.getFlows()).isEmpty();

    issue.setFlows(Arrays.asList(mock(ServerIssue.Flow.class), mock(ServerIssue.Flow.class)));
    assertThat(issue.getFlows()).hasSize(2);
  }
}
