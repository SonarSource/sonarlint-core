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

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.LibraryLoader;
import ch.usi.si.seart.treesitter.Node;
import ch.usi.si.seart.treesitter.Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Tree-sitter based code chunker that parses code into AST and extracts meaningful chunks.
 */
public class TreeSitterCodeChunker implements CodeChunker {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  
  static {
    try {
      LibraryLoader.load();
    } catch (Exception e) {
      LOG.error("Failed to load Tree-sitter native library", e);
    }
  }
  
  private static final Map<SonarLanguage, Language> LANGUAGE_MAP = Map.of(
    SonarLanguage.JAVA, Language.JAVA,
    SonarLanguage.JS, Language.JAVASCRIPT,
    SonarLanguage.XML, Language.XML,
    SonarLanguage.HTML, Language.HTML,
    SonarLanguage.JSON, Language.JSON
  );
  
  // Node types that represent meaningful code structures for different languages
  private static final Map<SonarLanguage, List<String>> CHUNK_NODE_TYPES = Map.of(
    SonarLanguage.JAVA, List.of("method_declaration", "class_declaration", "interface_declaration", 
                               "field_declaration", "import_declaration"),
    SonarLanguage.JS, List.of("function_declaration", "method_definition", "class_declaration", 
                             "variable_declaration", "import_statement", "export_statement"),
    SonarLanguage.XML, List.of("element", "self_closing_tag"),
    SonarLanguage.HTML, List.of("element", "self_closing_tag"),
    SonarLanguage.JSON, List.of("object", "array", "pair")
  );

  @Override
  public List<TextChunk> chunk(String content, SonarLanguage language, int maxChunkSize) {
    var tsLanguage = LANGUAGE_MAP.get(language);
    if (tsLanguage == null) {
      LOG.warn("Language {} not supported by TreeSitterCodeChunker, falling back to text chunking", language);
      return fallbackTextChunking(content, maxChunkSize);
    }

    try (var parser = Parser.getFor(tsLanguage)) {
      try (var tree = parser.parse(content)) {
        return extractChunks(tree.getRootNode(), content, language, maxChunkSize);
      }
    } catch (Exception e) {
      LOG.error("Error parsing code with Tree-sitter for language {}", language, e);
      return fallbackTextChunking(content, maxChunkSize);
    }
  }

  @Override
  public List<SonarLanguage> getSupportedLanguages() {
    return new ArrayList<>(LANGUAGE_MAP.keySet());
  }

  private List<TextChunk> extractChunks(Node rootNode, String content, SonarLanguage language, int maxChunkSize) {
    var chunks = new ArrayList<TextChunk>();
    var chunkNodeTypes = CHUNK_NODE_TYPES.get(language);
    
    if (chunkNodeTypes == null) {
      return fallbackTextChunking(content, maxChunkSize);
    }
    
    traverseAndExtractChunks(rootNode, content, chunkNodeTypes, maxChunkSize, chunks);
    
    if (chunks.isEmpty()) {
      // Fallback if no chunks were extracted
      return fallbackTextChunking(content, maxChunkSize);
    }
    
    return chunks;
  }

  private void traverseAndExtractChunks(Node node, String content, List<String> chunkNodeTypes, 
                                       int maxChunkSize, List<TextChunk> chunks) {
    var nodeType = node.getType();
    
    // Check if this is a node type we want to extract as a chunk
    if (chunkNodeTypes.contains(nodeType)) {
      var startByte = node.getStartByte();
      var endByte = node.getEndByte();
      var nodeText = content.substring(startByte, endByte);
      
      if (nodeText.length() <= maxChunkSize) {
        // Create chunk for this node
        var chunkType = mapNodeTypeToChunkType(nodeType);
        chunks.add(new TextChunk(startByte, endByte, nodeText, chunkType));
        return; // Don't traverse children if we've added this node as a chunk
      } else {
        // Node too large, try to split it by traversing children
        splitLargeNode(node, content, chunkNodeTypes, maxChunkSize, chunks);
        return;
      }
    }
    
    // Traverse children
    for (int i = 0; i < node.getChildCount(); i++) {
      var child = node.getChild(i);
      traverseAndExtractChunks(child, content, chunkNodeTypes, maxChunkSize, chunks);
    }
  }
  
  private void splitLargeNode(Node node, String content, List<String> chunkNodeTypes, 
                             int maxChunkSize, List<TextChunk> chunks) {
    // Try to find smaller chunks within this large node
    boolean foundChildChunks = false;
    
    for (int i = 0; i < node.getChildCount(); i++) {
      var child = node.getChild(i);
      var childStartByte = child.getStartByte();
      var childEndByte = child.getEndByte();
      var childText = content.substring(childStartByte, childEndByte);
      
      if (childText.length() <= maxChunkSize && chunkNodeTypes.contains(child.getType())) {
        var chunkType = mapNodeTypeToChunkType(child.getType());
        chunks.add(new TextChunk(childStartByte, childEndByte, childText, chunkType));
        foundChildChunks = true;
      } else {
        // Recursively try children
        traverseAndExtractChunks(child, content, chunkNodeTypes, maxChunkSize, chunks);
      }
    }
    
    // If no child chunks found, create text-based chunks for this node
    if (!foundChildChunks) {
      var startByte = node.getStartByte();
      var endByte = node.getEndByte();
      var nodeText = content.substring(startByte, endByte);
      var textChunks = createTextChunks(nodeText, startByte, maxChunkSize);
      chunks.addAll(textChunks);
    }
  }

  private TextChunk.ChunkType mapNodeTypeToChunkType(String nodeType) {
    return switch (nodeType) {
      case "method_declaration", "function_declaration", "method_definition" -> TextChunk.ChunkType.METHOD;
      case "class_declaration", "interface_declaration" -> TextChunk.ChunkType.CLASS;
      case "field_declaration", "variable_declaration" -> TextChunk.ChunkType.FIELD;
      case "import_declaration", "import_statement", "export_statement" -> TextChunk.ChunkType.IMPORT;
      case "element", "self_closing_tag" -> TextChunk.ChunkType.XML_ELEMENT;
      case "object", "array", "pair" -> TextChunk.ChunkType.YAML_SECTION;
      default -> TextChunk.ChunkType.BLOCK;
    };
  }

  private List<TextChunk> fallbackTextChunking(String content, int maxChunkSize) {
    return createTextChunks(content, 0, maxChunkSize);
  }
  
  private List<TextChunk> createTextChunks(String content, int baseOffset, int maxChunkSize) {
    var chunks = new ArrayList<TextChunk>();
    var lines = content.split("\n");
    var currentChunk = new StringBuilder();
    var chunkStartOffset = baseOffset;
    var currentOffset = baseOffset;
    
    for (var line : lines) {
      var lineWithNewline = line + "\n";
      
      if (currentChunk.length() + lineWithNewline.length() > maxChunkSize && currentChunk.length() > 0) {
        // Current chunk would exceed limit, save it and start new one
        chunks.add(new TextChunk(chunkStartOffset, currentOffset, currentChunk.toString().trim(), TextChunk.ChunkType.TEXT));
        currentChunk = new StringBuilder();
        chunkStartOffset = currentOffset;
      }
      
      currentChunk.append(lineWithNewline);
      currentOffset += lineWithNewline.length();
    }
    
    // Add the last chunk if it has content
    if (currentChunk.length() > 0) {
      chunks.add(new TextChunk(chunkStartOffset, currentOffset, currentChunk.toString().trim(), TextChunk.ChunkType.TEXT));
    }
    
    return chunks;
  }
}
