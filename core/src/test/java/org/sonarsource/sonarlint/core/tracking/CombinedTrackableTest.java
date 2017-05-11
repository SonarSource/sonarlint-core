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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

public class CombinedTrackableTest {
  @Test
  public void testMerge() {
    Trackable base = createMock("base", 0);
    Trackable next = createMock("next", 1);

    CombinedTrackable combined = new CombinedTrackable(base, next, true);

    assertThat(combined.getAssignee()).isEqualTo("baseAssignee");
    assertThat(combined.getServerIssueKey()).isEqualTo("baseServerIssueKey");
    assertThat(combined.getSeverity()).isEqualTo("baseSeverity");
    assertThat(combined.getType()).isEqualTo("baseType");
    assertThat(combined.getCreationDate()).isEqualTo(0);
    assertThat(combined.isResolved()).isEqualTo(false);

    assertThat(combined.getLine()).isEqualTo(1);
    assertThat(combined.getMessage()).isEqualTo("nextMessage");
    assertThat(combined.getLineHash()).isEqualTo(1);
    assertThat(combined.getRuleKey()).isEqualTo("nextRuleKey");
    assertThat(combined.getRuleName()).isEqualTo("nextRuleName");
    assertThat(combined.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(combined.getTextRangeHash()).isEqualTo(1);
  }

  private Trackable createMock(String name, int number) {
    Trackable t = mock(Trackable.class);
    when(t.getAssignee()).thenReturn(name + "Assignee");
    when(t.getCreationDate()).thenReturn((long) number);
    when(t.getLine()).thenReturn(number);
    when(t.getLineHash()).thenReturn(number);
    when(t.getMessage()).thenReturn(name + "Message");
    when(t.getRuleKey()).thenReturn(name + "RuleKey");
    when(t.getRuleName()).thenReturn(name + "RuleName");
    when(t.getServerIssueKey()).thenReturn(name + "ServerIssueKey");
    when(t.getSeverity()).thenReturn(name + "Severity");
    when(t.getTextRangeHash()).thenReturn(number);
    when(t.getType()).thenReturn(name + "Type");
    when(t.getTextRange()).thenReturn(new TextRange(number));
    when(t.isResolved()).thenReturn(number == 1);
    return t;
  }
}
