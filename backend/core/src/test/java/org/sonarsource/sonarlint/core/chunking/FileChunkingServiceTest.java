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

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.fs.ClientFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileChunkingServiceTest {

  @Mock
  private CodeChunker mockChunker;

  @Mock
  private ClientFile mockFile1;

  @Mock
  private ClientFile mockFile2;

  private FileChunkingService service;
  private URI testUri1;
  private URI testUri2;

  @BeforeEach
  void setUp() {
    testUri1 = URI.create("file:///test1.java");
    testUri2 = URI.create("file:///test2.js");
    
    service = new FileChunkingService(mockChunker, 512);
    
    when(mockFile1.getUri()).thenReturn(testUri1);
    when(mockFile1.getFileName()).thenReturn("test1.java");
    when(mockFile1.getDetectedLanguage()).thenReturn(SonarLanguage.JAVA);
    when(mockFile1.getContent()).thenReturn("public class Test {}");
    
    when(mockFile2.getUri()).thenReturn(testUri2);
    when(mockFile2.getFileName()).thenReturn("test2.js");
    when(mockFile2.getDetectedLanguage()).thenReturn(SonarLanguage.JS);
    when(mockFile2.getContent()).thenReturn("function test() {}");
    
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of(SonarLanguage.JAVA, SonarLanguage.JS));
  }

  @Test
  void should_chunk_single_file() {
    var expectedChunks = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS)
    );
    when(mockChunker.chunk("public class Test {}", SonarLanguage.JAVA, 512))
      .thenReturn(expectedChunks);

    var chunks = service.chunkFile(mockFile1);

    assertThat(chunks).isEqualTo(expectedChunks);
    verify(mockChunker).chunk("public class Test {}", SonarLanguage.JAVA, 512);
  }

  @Test
  void should_chunk_multiple_files() {
    var expectedChunks1 = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS)
    );
    var expectedChunks2 = List.of(
      new TextChunk(0, 17, "function test() {}", TextChunk.ChunkType.METHOD)
    );
    
    when(mockChunker.chunk("public class Test {}", SonarLanguage.JAVA, 512))
      .thenReturn(expectedChunks1);
    when(mockChunker.chunk("function test() {}", SonarLanguage.JS, 512))
      .thenReturn(expectedChunks2);

    var result = service.chunkFiles(List.of(mockFile1, mockFile2));

    assertThat(result).hasSize(2);
    assertThat(result.get(testUri1)).isEqualTo(expectedChunks1);
    assertThat(result.get(testUri2)).isEqualTo(expectedChunks2);
  }

  @Test
  void should_cache_chunks() {
    var expectedChunks = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS)
    );
    when(mockChunker.chunk("public class Test {}", SonarLanguage.JAVA, 512))
      .thenReturn(expectedChunks);

    // First call
    var chunks1 = service.chunkFile(mockFile1);
    // Second call should use cache
    var chunks2 = service.chunkFile(mockFile1);

    assertThat(chunks1).isEqualTo(expectedChunks);
    assertThat(chunks2).isEqualTo(expectedChunks);
    // Chunker should only be called once
    verify(mockChunker).chunk("public class Test {}", SonarLanguage.JAVA, 512);
  }

  @Test
  void should_return_cached_chunks() {
    var expectedChunks = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS)
    );
    when(mockChunker.chunk("public class Test {}", SonarLanguage.JAVA, 512))
      .thenReturn(expectedChunks);

    // Chunk the file first
    service.chunkFile(mockFile1);
    
    // Get cached chunks
    var cachedChunks = service.getCachedChunks(testUri1);

    assertThat(cachedChunks).isEqualTo(expectedChunks);
  }

  @Test
  void should_return_null_for_non_cached_file() {
    var cachedChunks = service.getCachedChunks(testUri1);

    assertThat(cachedChunks).isNull();
  }

  @Test
  void should_clear_specific_cache_entries() {
    var expectedChunks = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS)
    );
    when(mockChunker.chunk(any(), any(), eq(512))).thenReturn(expectedChunks);

    // Cache both files
    service.chunkFile(mockFile1);
    service.chunkFile(mockFile2);

    // Clear cache for one file
    service.clearCache(List.of(testUri1));

    assertThat(service.getCachedChunks(testUri1)).isNull();
    assertThat(service.getCachedChunks(testUri2)).isNotNull();
  }

  @Test
  void should_clear_all_cache() {
    var expectedChunks = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS)
    );
    when(mockChunker.chunk(any(), any(), eq(512))).thenReturn(expectedChunks);

    // Cache both files
    service.chunkFile(mockFile1);
    service.chunkFile(mockFile2);

    // Clear all cache
    service.clearAllCache();

    assertThat(service.getCachedChunks(testUri1)).isNull();
    assertThat(service.getCachedChunks(testUri2)).isNull();
  }

  @Test
  void should_handle_file_with_null_content() {
    when(mockFile1.getContent()).thenReturn(null);

    var chunks = service.chunkFile(mockFile1);

    assertThat(chunks).isEmpty();
    verify(mockChunker, never()).chunk(any(), any(), any());
  }

  @Test
  void should_handle_file_with_empty_content() {
    when(mockFile1.getContent()).thenReturn("");

    var chunks = service.chunkFile(mockFile1);

    assertThat(chunks).isEmpty();
    verify(mockChunker, never()).chunk(any(), any(), any());
  }

  @Test
  void should_handle_file_with_whitespace_only_content() {
    when(mockFile1.getContent()).thenReturn("   \n\t  ");

    var chunks = service.chunkFile(mockFile1);

    assertThat(chunks).isEmpty();
    verify(mockChunker, never()).chunk(any(), any(), any());
  }

  @Test
  void should_detect_language_from_extension_when_not_detected() {
    when(mockFile1.getDetectedLanguage()).thenReturn(null);
    when(mockFile1.getFileName()).thenReturn("test.java");
    
    var expectedChunks = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS)
    );
    when(mockChunker.chunk("public class Test {}", SonarLanguage.JAVA, 512))
      .thenReturn(expectedChunks);

    var chunks = service.chunkFile(mockFile1);

    assertThat(chunks).isEqualTo(expectedChunks);
    verify(mockChunker).chunk("public class Test {}", SonarLanguage.JAVA, 512);
  }

  @Test
  void should_use_fallback_chunking_for_unsupported_language() {
    when(mockFile1.getDetectedLanguage()).thenReturn(SonarLanguage.PYTHON);
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of(SonarLanguage.JAVA, SonarLanguage.JS));

    var chunks = service.chunkFile(mockFile1);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).allMatch(chunk -> chunk.getType() == TextChunk.ChunkType.TEXT);
    verify(mockChunker, never()).chunk(any(), eq(SonarLanguage.PYTHON), any());
  }

  @Test
  void should_return_statistics() {
    var expectedChunks = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS),
      new TextChunk(20, 40, "public void method() {}", TextChunk.ChunkType.METHOD)
    );
    when(mockChunker.chunk(any(), any(), eq(512))).thenReturn(expectedChunks);

    // Cache some files
    service.chunkFile(mockFile1);
    service.chunkFile(mockFile2);

    var stats = service.getStats();

    assertThat(stats.getCachedFiles()).isEqualTo(2);
    assertThat(stats.getTotalChunks()).isEqualTo(4); // 2 chunks per file
    assertThat(stats.getMaxChunkSize()).isEqualTo(512);
  }

  @Test
  void should_handle_chunking_exception_gracefully() {
    when(mockFile1.getContent()).thenThrow(new RuntimeException("File read error"));

    var chunks = service.chunkFile(mockFile1);

    assertThat(chunks).isEmpty();
  }

  @Test
  void should_skip_files_with_empty_chunks_in_batch_processing() {
    when(mockFile1.getContent()).thenReturn("valid content");
    when(mockFile2.getContent()).thenReturn(null); // Will result in empty chunks
    
    var expectedChunks = List.of(
      new TextChunk(0, 13, "valid content", TextChunk.ChunkType.CLASS)
    );
    when(mockChunker.chunk("valid content", SonarLanguage.JAVA, 512))
      .thenReturn(expectedChunks);

    var result = service.chunkFiles(List.of(mockFile1, mockFile2));

    assertThat(result).hasSize(1);
    assertThat(result).containsKey(testUri1);
    assertThat(result).doesNotContainKey(testUri2);
  }

  @Test
  void should_detect_various_file_extensions() {
    // Test different file extensions
    when(mockFile1.getDetectedLanguage()).thenReturn(null);
    
    when(mockFile1.getFileName()).thenReturn("test.jsx");
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of(SonarLanguage.JS));
    when(mockChunker.chunk(any(), eq(SonarLanguage.JS), eq(512)))
      .thenReturn(List.of(new TextChunk(0, 10, "test", TextChunk.ChunkType.TEXT)));
    
    var chunks = service.chunkFile(mockFile1);
    assertThat(chunks).isNotEmpty();
    
    // Test XML
    when(mockFile1.getFileName()).thenReturn("config.xml");
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of(SonarLanguage.XML));
    when(mockChunker.chunk(any(), eq(SonarLanguage.XML), eq(512)))
      .thenReturn(List.of(new TextChunk(0, 10, "test", TextChunk.ChunkType.XML_ELEMENT)));
    
    chunks = service.chunkFile(mockFile1);
    assertThat(chunks).isNotEmpty();
  }

  @Test
  void should_handle_file_without_extension() {
    when(mockFile1.getDetectedLanguage()).thenReturn(null);
    when(mockFile1.getFileName()).thenReturn("README");
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of(SonarLanguage.JAVA));

    var chunks = service.chunkFile(mockFile1);

    // Should use fallback chunking
    assertThat(chunks).isNotEmpty();
    assertThat(chunks).allMatch(chunk -> chunk.getType() == TextChunk.ChunkType.TEXT);
  }
}
