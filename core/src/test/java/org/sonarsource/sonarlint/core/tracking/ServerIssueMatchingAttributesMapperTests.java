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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerIssueMatchingAttributesMapperTests {

  private final LineLevelServerIssue serverIssue = mock(LineLevelServerIssue.class);
  private ServerIssueMatchingAttributesMapper underTest = new ServerIssueMatchingAttributesMapper();

  @BeforeEach
  void prepare() {
    when(serverIssue.getLineHash()).thenReturn("blah");
    when(serverIssue.isResolved()).thenReturn(true);
    when(serverIssue.getLine()).thenReturn(22);
  }

  @Test
  void should_delegate_fields_to_server_issue() {
    assertThat(underTest.getMessage(serverIssue)).isEqualTo(serverIssue.getMessage());
    assertThat(underTest.getLineHash(serverIssue)).contains(serverIssue.getLineHash());
    assertThat(underTest.getRuleKey(serverIssue)).isEqualTo(serverIssue.getRuleKey());
    assertThat(underTest.getLine(serverIssue)).contains(serverIssue.getLine());
  }
}
