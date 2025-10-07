/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.chunking;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextChunkTest {

  @Test
  void should_create_text_chunk_with_properties() {
    var chunk = new TextChunk(10, 50, "sample content", TextChunk.ChunkType.METHOD);

    assertThat(chunk.getStartOffset()).isEqualTo(10);
    assertThat(chunk.getEndOffset()).isEqualTo(50);
    assertThat(chunk.getContent()).isEqualTo("sample content");
    assertThat(chunk.getType()).isEqualTo(TextChunk.ChunkType.METHOD);
    assertThat(chunk.getSize()).isEqualTo(14); // "sample content".length()
  }

  @Test
  void should_implement_equals_correctly() {
    var chunk1 = new TextChunk(10, 50, "content", TextChunk.ChunkType.METHOD);
    var chunk2 = new TextChunk(10, 50, "content", TextChunk.ChunkType.METHOD);
    var chunk3 = new TextChunk(10, 50, "different", TextChunk.ChunkType.METHOD);
    var chunk4 = new TextChunk(20, 50, "content", TextChunk.ChunkType.METHOD);
    var chunk5 = new TextChunk(10, 50, "content", TextChunk.ChunkType.CLASS);

    assertThat(chunk1).isEqualTo(chunk2);
    assertThat(chunk1).isNotEqualTo(chunk3);
    assertThat(chunk1).isNotEqualTo(chunk4);
    assertThat(chunk1).isNotEqualTo(chunk5);
    assertThat(chunk1).isNotEqualTo(null);
    assertThat(chunk1).isNotEqualTo("not a chunk");
    assertThat(chunk1).isEqualTo(chunk1); // self equality
  }

  @Test
  void should_implement_hashcode_correctly() {
    var chunk1 = new TextChunk(10, 50, "content", TextChunk.ChunkType.METHOD);
    var chunk2 = new TextChunk(10, 50, "content", TextChunk.ChunkType.METHOD);
    var chunk3 = new TextChunk(10, 50, "different", TextChunk.ChunkType.METHOD);

    assertThat(chunk1.hashCode()).isEqualTo(chunk2.hashCode());
    assertThat(chunk1.hashCode()).isNotEqualTo(chunk3.hashCode());
  }

  @Test
  void should_have_meaningful_toString() {
    var chunk = new TextChunk(10, 50, "sample content", TextChunk.ChunkType.METHOD);
    var string = chunk.toString();

    assertThat(string).contains("METHOD");
    assertThat(string).contains("10");
    assertThat(string).contains("50");
    assertThat(string).contains("14"); // size
  }

  @Test
  void should_handle_empty_content() {
    var chunk = new TextChunk(0, 0, "", TextChunk.ChunkType.TEXT);

    assertThat(chunk.getSize()).isZero();
    assertThat(chunk.getContent()).isEmpty();
  }

  @Test
  void should_handle_large_content() {
    var largeContent = "a".repeat(1000);
    var chunk = new TextChunk(0, 1000, largeContent, TextChunk.ChunkType.TEXT);

    assertThat(chunk.getSize()).isEqualTo(1000);
    assertThat(chunk.getContent()).hasSize(1000);
  }

  @Test
  void should_test_all_chunk_types() {
    for (var chunkType : TextChunk.ChunkType.values()) {
      var chunk = new TextChunk(0, 10, "test", chunkType);
      assertThat(chunk.getType()).isEqualTo(chunkType);
    }
  }
}
