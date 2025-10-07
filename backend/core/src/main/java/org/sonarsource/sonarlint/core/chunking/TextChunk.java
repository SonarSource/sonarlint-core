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

import java.util.Objects;

/**
 * Represents a chunk of text with its position in the source file and optional context.
 */
public class TextChunk {
  
  private final int startOffset;
  private final int endOffset;
  private final String content;
  private final ChunkType type;
  private final int coreStartOffset;
  private final int coreEndOffset;
  
  /**
   * Creates a simple chunk without context (backward compatibility).
   */
  public TextChunk(int startOffset, int endOffset, String content, ChunkType type) {
    this(startOffset, endOffset, content, type, startOffset, endOffset);
  }
  
  /**
   * Creates a context-aware chunk with core content boundaries.
   * 
   * @param startOffset Start of the entire chunk (including context)
   * @param endOffset End of the entire chunk (including context)
   * @param content The complete content including context and ellipses
   * @param type The semantic type of the core chunk
   * @param coreStartOffset Start of the core content within the original file
   * @param coreEndOffset End of the core content within the original file
   */
  public TextChunk(int startOffset, int endOffset, String content, ChunkType type, 
                   int coreStartOffset, int coreEndOffset) {
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.content = content;
    this.type = type;
    this.coreStartOffset = coreStartOffset;
    this.coreEndOffset = coreEndOffset;
  }
  
  public int getStartOffset() {
    return startOffset;
  }
  
  public int getEndOffset() {
    return endOffset;
  }
  
  public String getContent() {
    return content;
  }
  
  public ChunkType getType() {
    return type;
  }
  
  public int getSize() {
    return content.length();
  }
  
  /**
   * Returns the start offset of the core content within the original file.
   */
  public int getCoreStartOffset() {
    return coreStartOffset;
  }
  
  /**
   * Returns the end offset of the core content within the original file.
   */
  public int getCoreEndOffset() {
    return coreEndOffset;
  }
  
  /**
   * Returns true if this chunk has context (i.e., includes surrounding code).
   */
  public boolean hasContext() {
    return startOffset != coreStartOffset || endOffset != coreEndOffset;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof TextChunk other)) {
      return false;
    }
    return startOffset == other.startOffset && 
           endOffset == other.endOffset && 
           Objects.equals(content, other.content) && 
           type == other.type &&
           coreStartOffset == other.coreStartOffset &&
           coreEndOffset == other.coreEndOffset;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(startOffset, endOffset, content, type, coreStartOffset, coreEndOffset);
  }
  
  @Override
  public String toString() {
    if (hasContext()) {
      return String.format("TextChunk{type=%s, range=[%d,%d], core=[%d,%d], size=%d}", 
                          type, startOffset, endOffset, coreStartOffset, coreEndOffset, getSize());
    } else {
      return String.format("TextChunk{type=%s, range=[%d,%d], size=%d}", 
                          type, startOffset, endOffset, getSize());
    }
  }
  
  /**
   * Types of chunks based on their semantic meaning
   */
  public enum ChunkType {
    /** A complete method/function */
    METHOD,
    /** A complete class */
    CLASS,
    /** A code block (if, for, while, etc.) */
    BLOCK,
    /** A field/property declaration */
    FIELD,
    /** An import/using statement */
    IMPORT,
    /** XML element */
    XML_ELEMENT,
    /** YAML section */
    YAML_SECTION,
    /** Generic text chunk when no specific semantic type applies */
    TEXT
  }
}
