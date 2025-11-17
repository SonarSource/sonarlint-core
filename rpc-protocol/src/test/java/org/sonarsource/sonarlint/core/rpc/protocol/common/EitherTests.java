/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

class EitherTests {

  @Test
  void testToString() {
    Either<String, Object> left = Either.forLeft("left");
    assertThat(left).hasToString(left.getLsp4jEither().toString());
    Either<String, Object> right = Either.forLeft("right");
    assertThat(right).hasToString(right.getLsp4jEither().toString());
  }

  @Test
  void testEquals() {
    Either<String, Object> left = Either.forLeft("left");
    assertThat(left).isEqualTo(left)
      .isEqualTo(Either.forLeft("left"))
      .isNotEqualTo(Either.forLeft("left_2"))
      .isNotEqualTo(null);
  }

}
