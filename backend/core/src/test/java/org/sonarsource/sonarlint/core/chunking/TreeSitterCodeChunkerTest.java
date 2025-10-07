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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterCodeChunkerTest {

  private TreeSitterCodeChunker chunker;

  @BeforeEach
  void setUp() {
    chunker = new TreeSitterCodeChunker();
  }

  @Test
  void should_return_supported_languages() {
    var supportedLanguages = chunker.getSupportedLanguages();

    assertThat(supportedLanguages).contains(
      SonarLanguage.JAVA,
      SonarLanguage.JS,
      SonarLanguage.XML,
      SonarLanguage.HTML,
      SonarLanguage.JSON
    );
  }

  @Test
  void should_chunk_simple_java_class() {
    var javaCode = """
      public class Example {
          public void method1() {
              System.out.println("Hello");
          }
          
          public void method2() {
              System.out.println("World");
          }
      }
      """;

    var chunks = chunker.chunk(javaCode, SonarLanguage.JAVA, 512);

    assertThat(chunks).isNotEmpty();
    // Should contain method chunks
    assertThat(chunks).anyMatch(chunk -> chunk.getType() == TextChunk.ChunkType.METHOD);
  }

  @Test
  void should_chunk_javascript_functions() {
    var jsCode = """
      function hello() {
          console.log("Hello");
      }
      
      function world() {
          console.log("World");
      }
      """;

    var chunks = chunker.chunk(jsCode, SonarLanguage.JS, 512);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).anyMatch(chunk -> chunk.getType() == TextChunk.ChunkType.METHOD);
  }

  @Test
  void should_chunk_xml_elements() {
    var xmlCode = """
      <root>
          <element1>Value1</element1>
          <element2>Value2</element2>
      </root>
      """;

    var chunks = chunker.chunk(xmlCode, SonarLanguage.XML, 512);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).anyMatch(chunk -> chunk.getType() == TextChunk.ChunkType.XML_ELEMENT);
  }

  @Test
  void should_handle_large_chunks_by_splitting() {
    var largeJavaMethod = """
      public class Example {
          public void largeMethod() {
              // This is a very long method that exceeds the chunk size limit
              System.out.println("Line 1");
              System.out.println("Line 2");
              System.out.println("Line 3");
              System.out.println("Line 4");
              System.out.println("Line 5");
              System.out.println("Line 6");
              System.out.println("Line 7");
              System.out.println("Line 8");
              System.out.println("Line 9");
              System.out.println("Line 10");
          }
      }
      """;

    var chunks = chunker.chunk(largeJavaMethod, SonarLanguage.JAVA, 100); // Small limit to force splitting

    assertThat(chunks).isNotEmpty();
    // All chunks should be within the size limit
    assertThat(chunks).allMatch(chunk -> chunk.getSize() <= 100);
  }

  @Test
  void should_fallback_to_text_chunking_for_unsupported_language() {
    var content = "Some random content for unsupported language";

    var chunks = chunker.chunk(content, SonarLanguage.PYTHON, 512);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).allMatch(chunk -> chunk.getType() == TextChunk.ChunkType.TEXT);
  }

  @Test
  void should_handle_empty_content() {
    var chunks = chunker.chunk("", SonarLanguage.JAVA, 512);

    assertThat(chunks).isEmpty();
  }

  @Test
  void should_handle_whitespace_only_content() {
    var chunks = chunker.chunk("   \n\t  ", SonarLanguage.JAVA, 512);

    assertThat(chunks).isEmpty();
  }

  @Test
  void should_handle_malformed_code_gracefully() {
    var malformedJava = "public class { invalid syntax }";

    var chunks = chunker.chunk(malformedJava, SonarLanguage.JAVA, 512);

    // Should fallback to text chunking
    assertThat(chunks).isNotEmpty();
  }

  @Test
  void should_respect_chunk_size_limit() {
    var javaCode = """
      public class Test {
          public void shortMethod() {
              System.out.println("short");
          }
      }
      """;

    var chunks = chunker.chunk(javaCode, SonarLanguage.JAVA, 50);

    assertThat(chunks).allMatch(chunk -> chunk.getSize() <= 50);
  }

  @Test
  void should_chunk_json_objects() {
    var jsonCode = """
      {
          "name": "example",
          "version": "1.0.0",
          "dependencies": {
              "lodash": "^4.17.21"
          }
      }
      """;

    var chunks = chunker.chunk(jsonCode, SonarLanguage.JSON, 512);

    assertThat(chunks).isNotEmpty();
  }

  @Test
  void should_chunk_html_elements() {
    var htmlCode = """
      <html>
          <head>
              <title>Test</title>
          </head>
          <body>
              <div>Content</div>
          </body>
      </html>
      """;

    var chunks = chunker.chunk(htmlCode, SonarLanguage.HTML, 512);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).anyMatch(chunk -> chunk.getType() == TextChunk.ChunkType.XML_ELEMENT);
  }

  @ParameterizedTest
  @EnumSource(value = SonarLanguage.class, names = {"JAVA", "JS", "XML", "HTML", "JSON"})
  void should_handle_all_supported_languages(SonarLanguage language) {
    var content = "simple content";

    var chunks = chunker.chunk(content, language, 512);

    assertThat(chunks).isNotNull();
  }

  @Test
  void should_preserve_content_in_small_chunks() {
    var javaCode = """
      public void test() {
          return;
      }
      """;

    var chunks = chunker.chunk(javaCode, SonarLanguage.JAVA, 512);

    assertThat(chunks).isNotEmpty();
    var concatenatedContent = chunks.stream()
        .map(TextChunk::getContent)
        .reduce("", String::concat);
    
    // The original content should be represented in the chunks
    assertThat(concatenatedContent).isNotEmpty();
  }

  @Test
  void should_handle_multiple_classes_in_java() {
    var javaCode = """
      public class First {
          public void method1() {}
      }
      
      public class Second {
          public void method2() {}
      }
      """;

    var chunks = chunker.chunk(javaCode, SonarLanguage.JAVA, 512);

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks).anyMatch(chunk -> chunk.getType() == TextChunk.ChunkType.CLASS);
  }
}
