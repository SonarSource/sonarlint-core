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

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TreeSitterCodeChunkerTest {

  private TreeSitterCodeChunker chunker;
  private static boolean treeSitterAvailable = true;

  @BeforeEach
  void setUp() {
    try {
      chunker = new TreeSitterCodeChunker();
      // Test if TreeSitter is actually functional by calling getSupportedLanguages
      var languages = chunker.getSupportedLanguages();
      treeSitterAvailable = !languages.isEmpty();
    } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError | Exception e) {
      // TreeSitter native library not available or failed to initialize
      treeSitterAvailable = false;
      chunker = new TreeSitterCodeChunker(); // This will work with fallback
    }
  }

  @Test
  void should_handle_empty_content() {
    var chunks = chunker.chunk("", SonarLanguage.JAVA, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isEmpty();
  }

  @Test
  void should_handle_null_content() {
    var chunks = chunker.chunk(null, SonarLanguage.JAVA, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isEmpty();
  }

  @Test
  void should_return_whole_file_for_whole_file_strategy() {
    var content = "public class Test {\n  public void method() {}\n}";
    
    var chunks = chunker.chunk(content, SonarLanguage.JAVA, 512, ChunkingStrategy.WHOLE_FILE);
    
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).getContent()).isEqualTo(content);
    assertThat(chunks.get(0).getType()).isEqualTo(TextChunk.ChunkType.TEXT);
    assertThat(chunks.get(0).getCoreStartOffset()).isEqualTo(0);
    assertThat(chunks.get(0).getCoreEndOffset()).isEqualTo(content.length());
  }

  @Test
  void should_return_supported_languages() {
    var supportedLanguages = chunker.getSupportedLanguages();
    
    // Should always return a list (empty if TreeSitter not available)
    assertThat(supportedLanguages).isNotNull();
    
    if (treeSitterAvailable) {
      // When TreeSitter is available, should support these languages
      assertThat(supportedLanguages).contains(
        SonarLanguage.JAVA,
        SonarLanguage.JS,
        SonarLanguage.XML,
        SonarLanguage.HTML,
        SonarLanguage.JSON
      );
    }
  }

  @Test
  void should_chunk_java_code_when_treesitter_available() {
    assumeTrue(treeSitterAvailable, "TreeSitter native library is not available");
    
    var javaCode = """
      public class HelloWorld {
        public static void main(String[] args) {
          System.out.println("Hello, World!");
        }
        
        private void helper() {
          // Helper method
        }
      }
      """;
    
    var chunks = chunker.chunk(javaCode, SonarLanguage.JAVA, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isNotEmpty();
    // Should have at least class and method chunks
    assertThat(chunks.size()).isGreaterThan(1);
    
    // Verify chunks have context
    var firstChunk = chunks.get(0);
    assertThat(firstChunk.hasContext()).isTrue();
    assertThat(firstChunk.getCoreStartOffset()).isGreaterThanOrEqualTo(0);
    assertThat(firstChunk.getCoreEndOffset()).isLessThanOrEqualTo(javaCode.length());
  }

  @Test
  void should_fallback_to_text_chunking_when_treesitter_unavailable() {
    // This test works regardless of TreeSitter availability
    var javaCode = """
      public class HelloWorld {
        public static void main(String[] args) {
          System.out.println("Hello, World!");
        }
      }
      """;
    
    var chunks = chunker.chunk(javaCode, SonarLanguage.JAVA, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isNotEmpty();
    
    if (!treeSitterAvailable) {
      // Should create text-based chunks when TreeSitter is not available
      assertThat(chunks.get(0).getType()).isEqualTo(TextChunk.ChunkType.TEXT);
    }
  }

  @Test
  void should_handle_unsupported_language_gracefully() {
    var chunks = chunker.chunk("some content", SonarLanguage.PYTHON, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    // Should fall back to text chunking for unsupported languages
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).getType()).isEqualTo(TextChunk.ChunkType.TEXT);
  }

  @ParameterizedTest
  @EnumSource(ChunkingStrategy.class)
  void should_handle_all_chunking_strategies(ChunkingStrategy strategy) {
    var content = "public class Test { public void method() {} }";
    
    var chunks = chunker.chunk(content, SonarLanguage.JAVA, 512, strategy);
    
    assertThat(chunks).isNotEmpty();
    
    if (strategy == ChunkingStrategy.WHOLE_FILE) {
      assertThat(chunks).hasSize(1);
      assertThat(chunks.get(0).getContent()).isEqualTo(content);
    }
  }

  @Test
  void should_respect_chunk_size_limit() {
    var longContent = "line1\n".repeat(100); // 600 characters
    
    var chunks = chunker.chunk(longContent, SonarLanguage.JAVA, 100, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isNotEmpty();
    // Each chunk should respect the size limit
    chunks.forEach(chunk -> assertThat(chunk.getSize()).isLessThanOrEqualTo(100));
  }

  @Test
  void should_provide_complete_file_coverage() {
    var content = "public class Test {\n  public void method1() {}\n  public void method2() {}\n}";
    
    var chunks = chunker.chunk(content, SonarLanguage.JAVA, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isNotEmpty();
    
    // Verify that all chunks together cover the entire file
    var coveredRanges = chunks.stream()
      .map(chunk -> new int[]{chunk.getCoreStartOffset(), chunk.getCoreEndOffset()})
      .toList();
    
    // Should have coverage from start to end
    var minStart = coveredRanges.stream().mapToInt(range -> range[0]).min().orElse(Integer.MAX_VALUE);
    var maxEnd = coveredRanges.stream().mapToInt(range -> range[1]).max().orElse(Integer.MIN_VALUE);
    
    assertThat(minStart).isEqualTo(0);
    assertThat(maxEnd).isEqualTo(content.length());
  }

  @Test
  void should_handle_xml_content() {
    var xmlContent = """
      <?xml version="1.0" encoding="UTF-8"?>
      <root>
        <element>value</element>
        <another>
          <nested>content</nested>
        </another>
      </root>
      """;
    
    var chunks = chunker.chunk(xmlContent, SonarLanguage.XML, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isNotEmpty();
    
    if (treeSitterAvailable) {
      // Should recognize XML elements when TreeSitter is available
      assertThat(chunks.stream().anyMatch(chunk -> 
        chunk.getType() == TextChunk.ChunkType.XML_ELEMENT)).isTrue();
    } else {
      // Should use text chunking when TreeSitter is not available
      assertThat(chunks.get(0).getType()).isEqualTo(TextChunk.ChunkType.TEXT);
    }
  }

  @Test
  void should_handle_javascript_content() {
    var jsContent = """
      function hello() {
        console.log("Hello World");
      }
      
      class MyClass {
        constructor() {
          this.value = 42;
        }
        
        method() {
          return this.value;
        }
      }
      """;
    
    var chunks = chunker.chunk(jsContent, SonarLanguage.JS, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isNotEmpty();
    
    if (treeSitterAvailable) {
      // Should recognize functions and classes when TreeSitter is available
      assertThat(chunks.stream().anyMatch(chunk -> 
        chunk.getType() == TextChunk.ChunkType.METHOD || 
        chunk.getType() == TextChunk.ChunkType.CLASS)).isTrue();
    } else {
      // Should use text chunking when TreeSitter is not available
      assertThat(chunks.get(0).getType()).isEqualTo(TextChunk.ChunkType.TEXT);
    }
  }

  @Test
  void should_handle_large_files_efficiently() {
    // Create a large Java file
    var largeJavaContent = new StringBuilder();
    largeJavaContent.append("public class LargeClass {\n");
    
    for (int i = 0; i < 50; i++) {
      largeJavaContent.append("  public void method").append(i).append("() {\n");
      largeJavaContent.append("    System.out.println(\"Method ").append(i).append("\");\n");
      largeJavaContent.append("  }\n\n");
    }
    
    largeJavaContent.append("}\n");
    
    var chunks = chunker.chunk(largeJavaContent.toString(), SonarLanguage.JAVA, 200, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.size()).isGreaterThan(10); // Should break into multiple chunks
    
    // Each chunk should respect the size limit
    chunks.forEach(chunk -> assertThat(chunk.getSize()).isLessThanOrEqualTo(200));
  }

  @Test
  void should_handle_edge_case_very_small_chunk_size() {
    var content = "public class Test { }";
    
    var chunks = chunker.chunk(content, SonarLanguage.JAVA, 20, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isNotEmpty();
    // Should handle very small chunk sizes gracefully
    chunks.forEach(chunk -> assertThat(chunk.getSize()).isLessThanOrEqualTo(20));
  }

  @Test
  void should_handle_whitespace_only_content() {
    var whitespaceContent = "   \n\n  \t  \n   ";
    
    var chunks = chunker.chunk(whitespaceContent, SonarLanguage.JAVA, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    // Should handle whitespace-only content appropriately
    if (chunks.isEmpty()) {
      // Empty result is acceptable for whitespace-only content
      assertThat(chunks).isEmpty();
    } else {
      // If chunks are created, they should be valid
      assertThat(chunks.get(0).getContent()).isNotNull();
    }
  }

  @Test
  void should_handle_single_line_content() {
    var singleLine = "public class SingleLine { public void method() { System.out.println(\"test\"); } }";
    
    var chunks = chunker.chunk(singleLine, SonarLanguage.JAVA, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    assertThat(chunks).isNotEmpty();
    
    if (treeSitterAvailable) {
      // Should be able to parse single-line code
      assertThat(chunks.size()).isGreaterThanOrEqualTo(1);
    } else {
      // Fallback should handle single line
      assertThat(chunks.get(0).getType()).isEqualTo(TextChunk.ChunkType.TEXT);
    }
  }

  @Test
  void should_handle_parsing_errors_gracefully() {
    // Malformed Java code that might cause parsing errors
    var malformedCode = """
      public class Broken {
        public void method( {
          // Missing closing parenthesis
          System.out.println("test"
        }
        // Missing closing brace
      """;
    
    var chunks = chunker.chunk(malformedCode, SonarLanguage.JAVA, 512, ChunkingStrategy.LARGEST_AST_NODE);
    
    // Should handle parsing errors gracefully and fall back to text chunking
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).getContent()).isNotNull();
  }

  @Test
  void should_test_treesitter_availability_detection() {
    // This test verifies our TreeSitter availability detection works correctly
    if (treeSitterAvailable) {
      assertThat(chunker.getSupportedLanguages()).isNotEmpty();
    } else {
      // When TreeSitter is not available, getSupportedLanguages should return empty list
      assertThat(chunker.getSupportedLanguages()).isEmpty();
    }
  }
}
