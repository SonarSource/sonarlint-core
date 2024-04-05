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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonarsource.sonarlint.core.analysis.sonarapi.MultivalueProperty.parseAsCsv;

class MultivaluePropertyTests {
  private static final String[] EMPTY_STRING_ARRAY = {};

  @ParameterizedTest
  @MethodSource("testParseAsCsv")
  void parseAsCsv_for_coverage(String value, String[] expected) {
    assertThat(parseAsCsv("key", value))
      .isEqualTo(expected);
  }

  @Test
  void parseAsCsv_fails_with_ISE_if_value_can_not_be_parsed() {
    assertThatThrownBy(() -> parseAsCsv("multi", "\"a ,b"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Property: 'multi' doesn't contain a valid CSV value: '\"a ,b'");
  }

  public static Stream<Arguments> testParseAsCsv() {
    return Stream.of(
      Arguments.of("", EMPTY_STRING_ARRAY),
      Arguments.of("a", arrayOf("a")),
      Arguments.of(" a", arrayOf("a")),
      Arguments.of("a ", arrayOf("a")),
      Arguments.of(" a, b", arrayOf("a", "b")),
      Arguments.of("a,b ", arrayOf("a", "b")),
      Arguments.of("a,,,b,c,,d", arrayOf("a", "b", "c", "d")),
      Arguments.of("a,\n\tb,\n   c,\n   d\n", arrayOf("a", "b", "c", "d")),
      Arguments.of("a\n\tb\n   c,\n   d\n", arrayOf("a\nb\nc", "d")),
      Arguments.of("\na\n\tb\n   c,\n   d\n", arrayOf("a\nb\nc", "d")),
      Arguments.of("a,\n,\nb", arrayOf("a", "b")),
      Arguments.of(" , \n ,, \t", EMPTY_STRING_ARRAY),
      Arguments.of("\" a\"", arrayOf(" a")),
      Arguments.of("\",\"", arrayOf(",")),
      // escaped quote in quoted field
      Arguments.of("\"\"\"\"", arrayOf("\"")));
  }

  private static String[] arrayOf(String... strs) {
    return strs;
  }

}
