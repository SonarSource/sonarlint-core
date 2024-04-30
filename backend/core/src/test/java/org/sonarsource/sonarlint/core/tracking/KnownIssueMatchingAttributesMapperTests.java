/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.KnownIssue;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnownIssueMatchingAttributesMapperTests {

  private final KnownIssue knownIssue = mock(KnownIssue.class);
  private final KnownIssueMatchingAttributesMapper underTest = new KnownIssueMatchingAttributesMapper();

  @BeforeEach
  void prepare() {
    when(knownIssue.getId()).thenReturn(UUID.randomUUID());
    when(knownIssue.getMessage()).thenReturn("msg");
    when(knownIssue.getRuleKey()).thenReturn("ruleKey");
    when(knownIssue.getTextRangeWithHash()).thenReturn(new TextRangeWithHash(1, 2, 3, 4, "rangehash"));
    when(knownIssue.getLineWithHash()).thenReturn(new LineWithHash(1, "linehash"));
  }

  @Test
  void should_delegate_fields_to_server_issue() {
    assertThat(underTest.getMessage(knownIssue)).isEqualTo(knownIssue.getMessage());
    assertThat(underTest.getRuleKey(knownIssue)).isEqualTo(knownIssue.getRuleKey());
    assertThat(underTest.getLine(knownIssue)).contains(knownIssue.getLineWithHash().getNumber());
    assertThat(underTest.getLineHash(knownIssue)).contains(knownIssue.getLineWithHash().getHash());
    assertThat(underTest.getTextRangeHash(knownIssue)).contains(knownIssue.getTextRangeWithHash().getHash());
    assertThat(underTest.getServerIssueKey(knownIssue)).isEmpty();
  }

}
