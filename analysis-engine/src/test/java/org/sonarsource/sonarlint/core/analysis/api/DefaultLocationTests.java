/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextPointer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DefaultLocationTests {
  @Test
  void verify_accessors() {
    var inputFile = mock(ClientInputFile.class);
    var message = "fummy";
    var sqApiTextRange = new DefaultTextRange(new DefaultTextPointer(1, 2), new DefaultTextPointer(3, 4));
    var defaultLocation = new DefaultLocation(inputFile, sqApiTextRange, message);

    assertThat(defaultLocation.getInputFile()).isSameAs(inputFile);
    assertThat(defaultLocation.getMessage()).isSameAs(message);
    assertThat(defaultLocation.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(defaultLocation.getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(defaultLocation.getTextRange().getEndLine()).isEqualTo(3);
    assertThat(defaultLocation.getTextRange().getEndLineOffset()).isEqualTo(4);
  }

  @Test
  void text_range_can_be_null() {
    var inputFile = mock(ClientInputFile.class);
    var message = "fummy";
    var defaultLocation = new DefaultLocation(inputFile, null, message);

    assertThat(defaultLocation.getTextRange()).isNull();
  }
}
