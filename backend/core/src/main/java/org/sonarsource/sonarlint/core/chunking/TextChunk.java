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
 * Represents a chunk of text with its position in the source file.
 */
public class TextChunk {
  
  private final int startOffset;
  private final int endOffset;
  private final String content;
  private final ChunkType type;
  
  public TextChunk(int startOffset, int endOffset, String content, ChunkType type) {
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.content = content;
    this.type = type;
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
           type == other.type;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(startOffset, endOffset, content, type);
  }
  
  @Override
  public String toString() {
    return String.format("TextChunk{type=%s, range=[%d,%d], size=%d}", 
                        type, startOffset, endOffset, getSize());
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
