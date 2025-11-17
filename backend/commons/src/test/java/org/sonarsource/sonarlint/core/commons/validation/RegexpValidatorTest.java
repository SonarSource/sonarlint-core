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

import java.util.Map;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexpValidatorTest {

  public static final String TEST_REGEXP = "[0-9]+";

  @Test
  void should_throw_exception_on_invalid_regexp() {
    assertThatThrownBy(() -> new RegexpValidator("[4-[8)"))
      .isInstanceOf(PatternSyntaxException.class);
  }

  @Test
  void should_return_empty_invalid_fields() {
    RegexpValidator validator = new RegexpValidator(TEST_REGEXP);

    InvalidFields invalidFields = validator.validateAll(Map.of(
      "field1", "12345",
      "field2", "455668",
      "field3", "0"
    ));

    assertThat(invalidFields.hasInvalidFields()).isFalse();
    assertThat(invalidFields.getNames()).isEmpty();
  }

  @Test
  void should_return_one_invalid_field() {
    RegexpValidator validator = new RegexpValidator(TEST_REGEXP);

    InvalidFields invalidFields = validator.validateAll(Map.of(
      "field1", "12345",
      "field2", "-455668",
      "field3", "0"
    ));

    assertThat(invalidFields.hasInvalidFields()).isTrue();
    assertThat(invalidFields.getNames())
      .containsExactlyInAnyOrder("field2");
  }

  @Test
  void should_return_all_invalid_fields() {
    RegexpValidator validator = new RegexpValidator(TEST_REGEXP);

    InvalidFields invalidFields = validator.validateAll(Map.of(
      "field1", "sqrt(12345)",
      "field2", "-455668",
      "field3", "^0^"
    ));

    assertThat(invalidFields.hasInvalidFields()).isTrue();
    assertThat(invalidFields.getNames())
      .containsExactlyInAnyOrder("field1", "field2", "field3");
  }
}
