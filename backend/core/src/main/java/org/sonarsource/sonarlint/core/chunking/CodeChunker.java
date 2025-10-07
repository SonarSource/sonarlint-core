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
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

/**
 * Interface for chunking code into meaningful pieces based on language-specific parsing.
 */
public interface CodeChunker {
  
  /**
   * Chunks the given content based on the specified language and configuration.
   *
   * @param content The source code content to chunk
   * @param language The detected language of the content
   * @param maxChunkSize Maximum size of each chunk in characters (ignored for WHOLE_FILE strategy)
   * @param strategy The chunking strategy to use
   * @return List of text chunks
   */
  List<TextChunk> chunk(String content, SonarLanguage language, int maxChunkSize, ChunkingStrategy strategy);
  
  /**
   * Chunks the given content based on the specified language and maximum chunk size using LARGEST_AST_NODE strategy.
   * This method is maintained for backward compatibility.
   *
   * @param content The source code content to chunk
   * @param language The detected language of the content
   * @param maxChunkSize Maximum size of each chunk in characters
   * @return List of text chunks that fit within the size limit
   */
  default List<TextChunk> chunk(String content, SonarLanguage language, int maxChunkSize) {
    return chunk(content, language, maxChunkSize, ChunkingStrategy.LARGEST_AST_NODE);
  }
  
  /**
   * Returns the languages supported by this chunker.
   *
   * @return Set of supported languages
   */
  List<SonarLanguage> getSupportedLanguages();
}
