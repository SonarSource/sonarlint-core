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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.List;

/** Edits one property in an already validated JSONC document without reserializing its surrounding text. */
final class JsoncPropertyEditor {
  private JsoncPropertyEditor() {
  }

  static String set(JsonMapper mapper, String source, List<String> path, JsonNode value) throws IOException {
    try (var parser = mapper.createParser(source)) {
      parser.nextToken();
      return set(mapper, parser, source, path, 0, value);
    }
  }

  private static String set(JsonMapper mapper, JsonParser parser, String source, List<String> path, int depth, JsonNode value) throws IOException {
    if (parser.currentToken() != JsonToken.START_OBJECT) {
      throw new IOException("The property's parent must be an object");
    }
    var insertionOffset = Math.toIntExact(parser.currentTokenLocation().getCharOffset()) + 1;
    var hasProperties = false;
    while (parser.nextToken() == JsonToken.FIELD_NAME) {
      hasProperties = true;
      var name = parser.currentName();
      parser.nextToken();
      if (name.equals(path.get(depth))) {
        if (depth + 1 < path.size()) {
          return set(mapper, parser, source, path, depth + 1, value);
        }
        var start = Math.toIntExact(parser.currentTokenLocation().getCharOffset());
        parser.skipChildren();
        // String tokens may be read lazily; finish them before taking the end offset.
        parser.finishToken();
        var end = Math.toIntExact(parser.currentLocation().getCharOffset());
        return source.substring(0, start) + value + source.substring(end);
      }
      parser.skipChildren();
    }
    var nestedValue = value;
    for (var index = path.size() - 1; index > depth; index--) {
      nestedValue = mapper.createObjectNode().set(path.get(index), nestedValue);
    }
    var property = mapper.writeValueAsString(path.get(depth)) + ":" + nestedValue;
    // Prepending avoids changing existing separators, including JSONC trailing commas and comments.
    return source.substring(0, insertionOffset) + property + (hasProperties ? "," : "") + source.substring(insertionOffset);
  }
}
