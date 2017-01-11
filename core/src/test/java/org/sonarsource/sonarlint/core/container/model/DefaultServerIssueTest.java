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
package org.sonarsource.sonarlint.core.container.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.Test;

public class DefaultServerIssueTest {
  @Test
  public void testRoundTrips() {
    DefaultServerIssue issue = new DefaultServerIssue();
    Instant i1 = Instant.ofEpochMilli(100_000_000);
    assertThat(issue.setChecksum("checksum1").checksum()).isEqualTo("checksum1");
    assertThat(issue.setCreationDate(i1).creationDate()).isEqualTo(i1);
    assertThat(issue.setAssigneeLogin("login1").assigneeLogin()).isEqualTo("login1");
    assertThat(issue.setFilePath("path1").filePath()).isEqualTo("path1");
    assertThat(issue.setKey("key1").key()).isEqualTo("key1");
    assertThat(issue.setLine(22).line()).isEqualTo(22);
    assertThat(issue.setManualSeverity(true).manualSeverity()).isEqualTo(true);
    assertThat(issue.setSeverity("MAJOR").severity()).isEqualTo("MAJOR");
    assertThat(issue.setRuleKey("rule1").ruleKey()).isEqualTo("rule1");
    assertThat(issue.setResolution("RESOLVED").resolution()).isEqualTo("RESOLVED");
    assertThat(issue.setModuleKey("module1").moduleKey()).isEqualTo("module1");
    assertThat(issue.setMessage("msg1").message()).isEqualTo("msg1");
  }
}
