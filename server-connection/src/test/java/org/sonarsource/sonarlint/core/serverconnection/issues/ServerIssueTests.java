/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.issues;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;

class ServerIssueTests {
  @Test
  void testRoundTrips() {
    var issue = aServerIssue();
    var i1 = Instant.ofEpochMilli(100_000_000);
    assertThat(issue.setCreationDate(i1).getCreationDate()).isEqualTo(i1);
    assertThat(issue.setFilePath("path1").getFilePath()).isEqualTo("path1");
    assertThat(issue.setKey("key1").getKey()).isEqualTo("key1");
    assertThat(issue.setUserSeverity("MAJOR").getUserSeverity()).isEqualTo("MAJOR");
    assertThat(issue.setRuleKey("rule1").getRuleKey()).isEqualTo("rule1");
    assertThat(issue.isResolved()).isTrue();
    assertThat(issue.setMessage("msg1").getMessage()).isEqualTo("msg1");
    assertThat(issue.setType("type").getType()).isEqualTo("type");
  }

}
