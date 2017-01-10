/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import static org.assertj.core.api.Assertions.assertThat;

public class TextRangeTest {
  @Test
  public void should_initialize_unknown_fields_with_null_for_line_only_TextRange() {
    int line = 7;
    TextRange lineOnlyTextRange = new TextRange(line);
    assertThat(lineOnlyTextRange.getStartLine()).isEqualTo(line);
    assertThat(lineOnlyTextRange.getStartLineOffset()).isNull();
    assertThat(lineOnlyTextRange.getEndLine()).isNull();
    assertThat(lineOnlyTextRange.getEndLineOffset()).isNull();
  }
}
