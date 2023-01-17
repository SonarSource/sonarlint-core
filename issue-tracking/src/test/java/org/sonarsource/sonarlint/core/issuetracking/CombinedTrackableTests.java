/*
 * SonarLint Issue Tracking
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
package org.sonarsource.sonarlint.core.issuetracking;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombinedTrackableTests {
  @Test
  void testMerge() {
    var base = createMock("base", 0, IssueSeverity.MINOR, RuleType.VULNERABILITY);
    var next = createMock("next", 1, IssueSeverity.BLOCKER, RuleType.SECURITY_HOTSPOT);

    var combined = new CombinedTrackable(base, next, true);

    assertThat(combined.getServerIssueKey()).isEqualTo("baseServerIssueKey");
    assertThat(combined.getSeverity()).isEqualTo(IssueSeverity.MINOR);
    assertThat(combined.getType()).isEqualTo(RuleType.VULNERABILITY);
    assertThat(combined.getCreationDate()).isZero();
    assertThat(combined.isResolved()).isFalse();

    assertThat(combined.getLine()).isEqualTo(1);
    assertThat(combined.getMessage()).isEqualTo("nextMessage");
    assertThat(combined.getLineHash()).isEqualTo("lineHash1");
    assertThat(combined.getRuleKey()).isEqualTo("nextRuleKey");
    assertThat(combined.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(combined.getTextRange().getHash()).isEqualTo("md51");
  }

  private Trackable createMock(String name, int number, IssueSeverity severity, RuleType type) {
    var t = mock(Trackable.class);
    when(t.getCreationDate()).thenReturn((long) number);
    when(t.getLine()).thenReturn(number);
    when(t.getLineHash()).thenReturn("lineHash" + number);
    when(t.getMessage()).thenReturn(name + "Message");
    when(t.getRuleKey()).thenReturn(name + "RuleKey");
    when(t.getServerIssueKey()).thenReturn(name + "ServerIssueKey");
    when(t.getSeverity()).thenReturn(severity);
    when(t.getType()).thenReturn(type);
    when(t.getTextRange()).thenReturn(new TextRangeWithHash(number, 2, 3, 4, "md5" + number));
    when(t.isResolved()).thenReturn(number == 1);
    return t;
  }
}
