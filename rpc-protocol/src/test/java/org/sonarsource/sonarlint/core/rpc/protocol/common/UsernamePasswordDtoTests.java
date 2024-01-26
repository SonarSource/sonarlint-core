/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class UsernamePasswordDtoTests {


  @Test
  void testEqualsAndHashCode() {
    var up1 = new UsernamePasswordDto("user1", "password1");
    var sameUp = new UsernamePasswordDto("user1", "password1");
    var differentUp = new UsernamePasswordDto("user2", "password1");
    var differentUp2 = new UsernamePasswordDto("user1", "password2");

    // Assuming that two new instances are equal
    assertThat(up1)
      .isEqualTo(up1)
      .isEqualTo(sameUp)
      .isNotEqualTo(differentUp)
      .isNotEqualTo(differentUp2)
      .isNotEqualTo("token1")
      .hasSameHashCodeAs(sameUp)
      .doesNotHaveSameHashCodeAs(differentUp)
      .doesNotHaveSameHashCodeAs(differentUp2);
  }
}