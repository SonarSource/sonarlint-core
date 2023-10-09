/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.clientapi.common;

public enum CleanCodeAttribute {

  CONVENTIONAL(1),
  FORMATTED(2),
  IDENTIFIABLE(3),

  CLEAR(4),
  COMPLETE(5),
  EFFICIENT(6),
  LOGICAL(7),

  DISTINCT(8),
  FOCUSED(9),
  MODULAR(10),
  TESTED(11),

  LAWFUL(12),
  RESPECTFUL(13),
  TRUSTWORTHY(14);

  private final int value;

  CleanCodeAttribute(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
