/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.ai.ide;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsoncPropertyEditorTests {
  private static final JsonMapper MAPPER = JsonMapper.builder()
    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
    .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    .build();

  @ParameterizedTest
  @ValueSource(ints = {0, 3990, 4000, 32760, 32768, 70000})
  void should_preserve_offsets_across_parser_buffers_and_large_string_inputs(int padding) throws IOException {
    var prefix = " ".repeat(padding) + "{\"skipped\":[{\"selected\":\"é🐈\"}],\"env\":{\"selected\":";
    var oldValue = MAPPER.writeValueAsString("é🐈\\\"/\n".repeat(1000));
    var suffix = ",\"unchanged\":\"\\u0041\"}} /* end */";
    var source = prefix + oldValue + suffix;
    var replacement = MAPPER.getNodeFactory().textNode("64120");
    var expected = (ObjectNode) MAPPER.readTree(source);
    ((ObjectNode) expected.path("env")).set("selected", replacement);

    var updated = JsoncPropertyEditor.set(MAPPER, source, List.of("env", "selected"), replacement);

    assertThat(updated).isEqualTo(prefix + "\"64120\"" + suffix);
    assertThat(MAPPER.readTree(updated)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "quote\"", "backslash\\", "line\n\t\u0000", "é🐈", "a.b/[0]"})
  void should_escape_inserted_keys_and_values_without_changing_existing_text(String key) throws IOException {
    var prefix = "/* 🐈 */{\"env\":{";
    var suffix = "\r\n// Keep this comment and the trailing comma\r\n\"OTHER\":{},}}\r\n";
    var source = prefix + suffix;
    var replacement = MAPPER.getNodeFactory().textNode("quote\" and backslash\\ and newline\n and 🐈");
    var nested = MAPPER.createObjectNode().set("nested\"\\\n🐈", replacement);
    var expected = (ObjectNode) MAPPER.readTree(source);
    ((ObjectNode) expected.path("env")).set(key, nested);

    var updated = JsoncPropertyEditor.set(MAPPER, source, List.of("env", key, "nested\"\\\n🐈"), replacement);

    assertThat(updated).isEqualTo(prefix + MAPPER.writeValueAsString(key) + ":" + nested + "," + suffix);
    assertThat(MAPPER.readTree(updated)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " \t", "/* } \"selected\":null */", "\n// }\n", "\r\n// 🐈\r\n"})
  void should_insert_into_comment_only_objects_without_adding_a_stray_comma(String trivia) throws IOException {
    var prefix = "/*root*/{\"env\":{";
    var suffix = "}}// final";
    var source = prefix + trivia + suffix;
    var replacement = MAPPER.getNodeFactory().textNode("64120");
    var expected = (ObjectNode) MAPPER.readTree(source);
    ((ObjectNode) expected.path("env")).set("selected", replacement);

    var updated = JsoncPropertyEditor.set(MAPPER, source, List.of("env", "selected"), replacement);

    assertThat(updated).isEqualTo(prefix + "\"selected\":\"64120\"" + trivia + suffix);
    assertThat(MAPPER.readTree(updated)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "[]", "true", "1", "\"text\""})
  void should_refuse_to_replace_a_non_object_parent(String parent) throws IOException {
    var source = "{\"env\":" + parent + "}";
    MAPPER.readTree(source);

    assertThatThrownBy(() -> JsoncPropertyEditor.set(MAPPER, source, List.of("env", "selected"), MAPPER.getNodeFactory().textNode("64120")))
      .isInstanceOf(IOException.class)
      .hasMessage("The property's parent must be an object");
  }
}
