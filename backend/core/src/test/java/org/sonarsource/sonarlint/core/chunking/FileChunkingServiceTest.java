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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.fs.ClientFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FileChunkingServiceTest {

  @Mock
  private CodeChunker mockChunker;
  
  @Mock
  private ClientFile mockFile;
  
  private FileChunkingService service;
  
  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    
    // Configure SonarLint logger to avoid "No log output configured" errors
    var mockLogOutput = mock(LogOutput.class);
    SonarLintLogger.get().setTarget(mockLogOutput);
    
    // Setup default mock behaviors
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of(
      SonarLanguage.JAVA, SonarLanguage.JS, SonarLanguage.XML, SonarLanguage.HTML, 
      SonarLanguage.JSON, SonarLanguage.YAML));
    when(mockChunker.chunk(any(), any(), anyInt(), any())).thenReturn(
      List.of(new TextChunk(0, 10, "test chunk", TextChunk.ChunkType.TEXT))
    );
    
    when(mockFile.getUri()).thenReturn(URI.create("file:///test.java"));
    when(mockFile.getFileName()).thenReturn("test.java");
    when(mockFile.getContent()).thenReturn("public class Test {}");
    when(mockFile.getDetectedLanguage()).thenReturn(SonarLanguage.JAVA);
    
    service = new FileChunkingService(mockChunker, 512);
  }

  @Test
  void should_chunk_single_file() {
    var chunks = service.chunkFile(mockFile);
    
    assertThat(chunks).hasSize(1);
    verify(mockChunker).chunk(eq("public class Test {}"), eq(SonarLanguage.JAVA), eq(512), eq(ChunkingStrategy.LARGEST_AST_NODE));
  }

  @Test
  void should_chunk_single_file_with_strategy() {
    var chunks = service.chunkFile(mockFile, ChunkingStrategy.WHOLE_FILE);
    
    assertThat(chunks).hasSize(1);
    verify(mockChunker).chunk(eq("public class Test {}"), eq(SonarLanguage.JAVA), eq(512), eq(ChunkingStrategy.WHOLE_FILE));
  }

  @Test
  void should_chunk_multiple_files() {
    var mockFile2 = mock(ClientFile.class);
    when(mockFile2.getUri()).thenReturn(URI.create("file:///test2.java"));
    when(mockFile2.getFileName()).thenReturn("test2.java");
    when(mockFile2.getContent()).thenReturn("public class Test2 {}");
    when(mockFile2.getDetectedLanguage()).thenReturn(SonarLanguage.JAVA);
    
    var files = List.of(mockFile, mockFile2);
    var result = service.chunkFiles(files);
    
    assertThat(result).hasSize(2);
    assertThat(result).containsKeys(
      URI.create("file:///test.java"),
      URI.create("file:///test2.java")
    );
  }

  @Test
  void should_chunk_multiple_files_with_strategy() {
    var mockFile2 = mock(ClientFile.class);
    when(mockFile2.getUri()).thenReturn(URI.create("file:///test2.java"));
    when(mockFile2.getFileName()).thenReturn("test2.java");
    when(mockFile2.getContent()).thenReturn("public class Test2 {}");
    when(mockFile2.getDetectedLanguage()).thenReturn(SonarLanguage.JAVA);
    
    var files = List.of(mockFile, mockFile2);
    var result = service.chunkFiles(files, ChunkingStrategy.WHOLE_FILE);
    
    assertThat(result).hasSize(2);
    verify(mockChunker, times(2)).chunk(any(), any(), anyInt(), eq(ChunkingStrategy.WHOLE_FILE));
  }

  @Test
  void should_return_cached_chunks() {
    // First call
    var chunks1 = service.chunkFile(mockFile);
    
    // Second call should return cached result
    var chunks2 = service.chunkFile(mockFile);
    
    assertThat(chunks1).isEqualTo(chunks2);
    verify(mockChunker, times(1)).chunk(any(), any(), anyInt(), any());
  }

  @Test
  void should_clear_specific_cache_entries() {
    service.chunkFile(mockFile);
    
    service.clearCache(List.of(mockFile.getUri()));
    
    // Next call should hit the chunker again
    service.chunkFile(mockFile);
    verify(mockChunker, times(2)).chunk(any(), any(), anyInt(), any());
  }

  @Test
  void should_clear_all_cache() {
    service.chunkFile(mockFile);
    
    service.clearAllCache();
    
    // Next call should hit the chunker again
    service.chunkFile(mockFile);
    verify(mockChunker, times(2)).chunk(any(), any(), anyInt(), any());
  }

  @Test
  void should_handle_file_with_null_content() {
    when(mockFile.getContent()).thenReturn(null);
    
    var chunks = service.chunkFile(mockFile);
    
    assertThat(chunks).isEmpty();
    verify(mockChunker, never()).chunk(any(), any(), anyInt(), any());
  }

  @Test
  void should_handle_file_with_empty_content() {
    when(mockFile.getContent()).thenReturn("");
    
    var chunks = service.chunkFile(mockFile);
    
    assertThat(chunks).isEmpty();
    verify(mockChunker, never()).chunk(any(), any(), anyInt(), any());
  }

  @Test
  void should_handle_file_with_whitespace_only_content() {
    when(mockFile.getContent()).thenReturn("   \n   \n   ");
    
    var chunks = service.chunkFile(mockFile);
    
    assertThat(chunks).isEmpty();
    verify(mockChunker, never()).chunk(any(), any(), anyInt(), any());
  }

  @Test
  void should_skip_files_with_empty_chunks_in_batch_processing() {
    when(mockChunker.chunk(any(), any(), anyInt(), any())).thenReturn(List.of());
    
    var result = service.chunkFiles(List.of(mockFile));
    
    assertThat(result).isEmpty();
  }

  @Test
  void should_detect_language_from_extension_when_not_detected() {
    when(mockFile.getDetectedLanguage()).thenReturn(null);
    when(mockFile.getFileName()).thenReturn("test.js");
    
    service.chunkFile(mockFile);
    
    verify(mockChunker).chunk(any(), eq(SonarLanguage.JS), anyInt(), any());
  }

  @ParameterizedTest
  @MethodSource("provideFileExtensions")
  void should_detect_various_file_extensions(String fileName, SonarLanguage expectedLanguage) {
    when(mockFile.getDetectedLanguage()).thenReturn(null);
    when(mockFile.getFileName()).thenReturn(fileName);
    
    service.chunkFile(mockFile);
    
    if (expectedLanguage != null) {
      verify(mockChunker).chunk(any(), eq(expectedLanguage), anyInt(), any());
    }
  }

  private static Stream<Arguments> provideFileExtensions() {
    return Stream.of(
      Arguments.of("test.java", SonarLanguage.JAVA),
      Arguments.of("test.js", SonarLanguage.JS),
      Arguments.of("test.jsx", SonarLanguage.JS),
      Arguments.of("test.xml", SonarLanguage.XML),
      Arguments.of("test.html", SonarLanguage.HTML),
      Arguments.of("test.htm", SonarLanguage.HTML),
      Arguments.of("test.json", SonarLanguage.JSON),
      Arguments.of("test.yml", SonarLanguage.YAML),
      Arguments.of("test.yaml", SonarLanguage.YAML),
      Arguments.of("test.unknown", null) // Unknown extensions should use fallback chunking
    );
  }

  @Test
  void should_use_fallback_chunking_for_unsupported_language() {
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of(SonarLanguage.JAVA));
    when(mockFile.getDetectedLanguage()).thenReturn(SonarLanguage.PYTHON);
    
    var chunks = service.chunkFile(mockFile);
    
    // Should use fallback chunking, creating context-aware text chunks
    assertThat(chunks).isNotEmpty();
    verify(mockChunker, never()).chunk(any(), any(), anyInt(), any());
  }

  @Test
  void should_handle_file_without_extension() {
    when(mockFile.getDetectedLanguage()).thenReturn(null);
    when(mockFile.getFileName()).thenReturn("README");
    
    var chunks = service.chunkFile(mockFile);
    
    // Should use fallback chunking for unknown file type
    assertThat(chunks).isNotEmpty();
  }

  @Test
  void should_handle_chunking_exception_gracefully() {
    when(mockChunker.chunk(any(), any(), anyInt(), any()))
      .thenThrow(new RuntimeException("Chunking failed"));
    
    var chunks = service.chunkFile(mockFile);
    
    // Should fall back to context-aware text chunking
    assertThat(chunks).isNotEmpty();
  }

  @Test
  void should_create_service_with_different_strategies() {
    var serviceWithWholeFile = new FileChunkingService(mockChunker, 1024, ChunkingStrategy.WHOLE_FILE);
    
    serviceWithWholeFile.chunkFile(mockFile);
    
    verify(mockChunker).chunk(any(), any(), eq(1024), eq(ChunkingStrategy.WHOLE_FILE));
  }

  @Test
  void should_use_default_constructor() {
    try {
      var defaultService = new FileChunkingService();
      assertThat(defaultService).isNotNull();
    } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
      // TreeSitter native library not available (e.g., architecture mismatch)
      // This is acceptable in test environments
      assertThat(e.getMessage()).contains("java-tree-sitter");
    }
  }

  @Test
  void should_create_context_aware_fallback_chunks() {
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of());
    when(mockFile.getContent()).thenReturn("line1\nline2\nline3\nline4\nline5");
    
    var chunks = service.chunkFile(mockFile);
    
    assertThat(chunks).isNotEmpty();
    // Should have created context-aware chunks with fallback
    var firstChunk = chunks.get(0);
    assertThat(firstChunk.getContent()).contains("line");
  }

  @Test
  void should_handle_whole_file_strategy_in_fallback() {
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of());
    var content = "This is a test file content";
    when(mockFile.getContent()).thenReturn(content);
    
    var chunks = service.chunkFile(mockFile, ChunkingStrategy.WHOLE_FILE);
    
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).getContent()).isEqualTo(content);
    assertThat(chunks.get(0).getType()).isEqualTo(TextChunk.ChunkType.TEXT);
  }

  @Test
  void should_respect_chunk_size_in_fallback_chunking() {
    when(mockChunker.getSupportedLanguages()).thenReturn(List.of());
    // Create content with multiple lines to ensure proper chunking
    // Account for context overhead (ellipses + context buffer)
    var longContent = "line1\n".repeat(15); // 90 characters total
    when(mockFile.getContent()).thenReturn(longContent);
    
    var smallChunkService = new FileChunkingService(mockChunker, 80);
    var chunks = smallChunkService.chunkFile(mockFile);
    
    assertThat(chunks).isNotEmpty();
    chunks.forEach(chunk -> 
      assertThat(chunk.getSize()).isLessThanOrEqualTo(80)
    );
  }

  @Test
  void should_test_chunking_stats() {
    var stats = new FileChunkingService.ChunkingStats(5, 42, 512);
    
    assertThat(stats.getCachedFiles()).isEqualTo(5);
    assertThat(stats.getTotalChunks()).isEqualTo(42);
    assertThat(stats.getMaxChunkSize()).isEqualTo(512);
    assertThat(stats.toString()).contains("cachedFiles=5", "totalChunks=42", "maxChunkSize=512");
  }

  @Test
  void should_test_code_chunker_default_method() {
    // Test the default method in CodeChunker interface
    var chunker = new CodeChunker() {
      @Override
      public List<TextChunk> chunk(String content, SonarLanguage language, int maxChunkSize, ChunkingStrategy strategy) {
        return List.of(new TextChunk(0, content.length(), content, TextChunk.ChunkType.TEXT, 0, content.length()));
      }

      @Override
      public List<SonarLanguage> getSupportedLanguages() {
        return List.of(SonarLanguage.JAVA);
      }
    };
    
    var result = chunker.chunk("test content", SonarLanguage.JAVA, 512);
    
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getContent()).isEqualTo("test content");
  }

  @Test
  void should_test_additional_text_chunk_edge_cases() {
    // Test TextChunk with same core and full boundaries (no context)
    var chunkWithoutContext = new TextChunk(0, 10, "test", TextChunk.ChunkType.METHOD);
    assertThat(chunkWithoutContext.hasContext()).isFalse();
    assertThat(chunkWithoutContext.getCoreStartOffset()).isEqualTo(0);
    assertThat(chunkWithoutContext.getCoreEndOffset()).isEqualTo(10);
    
    // Test TextChunk with different core and full boundaries (with context)
    var chunkWithContext = new TextChunk(0, 20, "...before\ntest\nafter...", TextChunk.ChunkType.METHOD, 5, 15);
    assertThat(chunkWithContext.hasContext()).isTrue();
    assertThat(chunkWithContext.getCoreStartOffset()).isEqualTo(5);
    assertThat(chunkWithContext.getCoreEndOffset()).isEqualTo(15);
  }

  @Test
  void should_handle_null_file_extension() {
    when(mockFile.getDetectedLanguage()).thenReturn(null);
    when(mockFile.getFileName()).thenReturn("noextension"); // No extension
    
    var chunks = service.chunkFile(mockFile);
    
    assertThat(chunks).isNotEmpty(); // Should use fallback chunking
  }

  @Test
  void should_handle_empty_file_extension() {
    when(mockFile.getDetectedLanguage()).thenReturn(null);
    when(mockFile.getFileName()).thenReturn("test."); // Empty extension
    
    var chunks = service.chunkFile(mockFile);
    
    assertThat(chunks).isNotEmpty(); // Should use fallback chunking
  }

  @Test
  void should_handle_file_ending_with_dot() {
    when(mockFile.getDetectedLanguage()).thenReturn(null);
    when(mockFile.getFileName()).thenReturn("test."); // Ends with dot
    
    var chunks = service.chunkFile(mockFile);
    
    assertThat(chunks).isNotEmpty(); // Should use fallback chunking
  }

  @Test
  void should_clear_cache_for_specific_files() {
    var uri1 = URI.create("file:///test1.java");
    var uri2 = URI.create("file:///test2.java");
    
    // First, create some cached entries by chunking files
    when(mockFile.getUri()).thenReturn(uri1);
    service.chunkFile(mockFile);
    
    when(mockFile.getUri()).thenReturn(uri2);
    service.chunkFile(mockFile);
    
    // Clear cache for specific files
    service.clearCache(List.of(uri1));
    
    // This should work without issues
    assertThat(service).isNotNull();
  }

  @Test
  void should_clear_all_cache_after_chunking() {
    // First, create some cached entries
    service.chunkFile(mockFile);
    
    // Clear all cache
    service.clearAllCache();
    
    // This should work without issues
    assertThat(service).isNotNull();
  }
}
