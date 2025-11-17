/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidFieldsTest {

  public static final String[] EXPECTED = {"name1", "name2", "name3"};

  @Test
  void should_have_no_invalid_fields_initially() {
    InvalidFields tested = new InvalidFields();

    assertThat(tested.hasInvalidFields()).isFalse();
  }

  @Test
  void should_have_invalid_fields_after_adding_one() {
    InvalidFields tested = new InvalidFields();

    tested.add("name1");

    assertThat(tested.hasInvalidFields()).isTrue();
  }

  @Test
  void should_include_all_added_fields() {
    InvalidFields tested = new InvalidFields();

    tested.add("name1");
    tested.add("name2");
    tested.add("name3");
    String[] names = tested.getNames();

    assertThat(names).containsExactly(EXPECTED);
  }
}
