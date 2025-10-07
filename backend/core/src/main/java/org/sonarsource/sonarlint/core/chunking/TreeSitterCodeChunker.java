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
 * Tree-sitter based code chunker that parses code into AST and extracts meaningful chunks with context.
 */
public class TreeSitterCodeChunker implements CodeChunker {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final boolean TREE_SITTER_AVAILABLE;
  private static final Map<SonarLanguage, Language> LANGUAGE_MAP;
  
  static {
    boolean available = false;
    Map<SonarLanguage, Language> languageMap = Map.of();
    
    try {
      LibraryLoader.load();
      languageMap = Map.of(
        SonarLanguage.JAVA, Language.JAVA,
        SonarLanguage.JS, Language.JAVASCRIPT,
        SonarLanguage.XML, Language.XML,
        SonarLanguage.HTML, Language.HTML,
        SonarLanguage.JSON, Language.JSON
      );
      available = true;
    } catch (Exception e) {
      LOG.warn("Failed to load Tree-sitter native library, will fall back to text chunking: {}", e.getMessage());
    }
    
    TREE_SITTER_AVAILABLE = available;
    LANGUAGE_MAP = languageMap;
  }
  
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

  // Constants for context formatting
  private static final String ELLIPSIS = "...";
  private static final int CONTEXT_BUFFER_SIZE = 50; // Characters of context before/after core chunk
  
  @Override
  public List<TextChunk> chunk(String content, SonarLanguage language, int maxChunkSize, ChunkingStrategy strategy) {
    if (content == null || content.isEmpty()) {
      return List.of();
    }

    if (strategy == ChunkingStrategy.WHOLE_FILE) {
      return List.of(new TextChunk(0, content.length(), content, TextChunk.ChunkType.TEXT, 0, content.length()));
    }
    
    // If TreeSitter is not available, fall back to text chunking immediately
    if (!TREE_SITTER_AVAILABLE) {
      return fallbackTextChunking(content, maxChunkSize);
    }
    
    var tsLanguage = LANGUAGE_MAP.get(language);
    if (tsLanguage == null) {
      LOG.warn("Language {} not supported by TreeSitterCodeChunker, falling back to text chunking", language);
      return fallbackTextChunking(content, maxChunkSize);
    }

    try (var parser = Parser.getFor(tsLanguage)) {
      try (var tree = parser.parse(content)) {
        return extractChunksWithContext(tree.getRootNode(), content, language, maxChunkSize);
      }
    } catch (Exception e) {
      LOG.warn("Error parsing code with Tree-sitter for language {}, falling back to text chunking: {}", language, e.getMessage());
      return fallbackTextChunking(content, maxChunkSize);
    }
  }

  @Override
  public List<SonarLanguage> getSupportedLanguages() {
    return new ArrayList<>(LANGUAGE_MAP.keySet());
  }

  private List<TextChunk> extractChunksWithContext(Node rootNode, String content, SonarLanguage language, int maxChunkSize) {
    var chunks = new ArrayList<TextChunk>();
    var chunkNodeTypes = CHUNK_NODE_TYPES.get(language);
    
    if (chunkNodeTypes == null) {
      return fallbackTextChunking(content, maxChunkSize);
    }
    
    // First, collect all core chunks without context
    var coreChunks = new ArrayList<CoreChunk>();
    traverseAndCollectCoreChunks(rootNode, content, chunkNodeTypes, maxChunkSize, coreChunks);
    
    if (coreChunks.isEmpty()) {
      // Fallback if no chunks were extracted
      return fallbackTextChunking(content, maxChunkSize);
    }
    
    // Sort chunks by start position to ensure complete coverage
    coreChunks.sort((a, b) -> Integer.compare(a.startOffset, b.startOffset));
    
    // Fill gaps between chunks to ensure complete file coverage
    var completeCoreChunks = fillGaps(coreChunks, content, maxChunkSize);
    
    // Add context to each chunk
    return addContextToChunks(completeCoreChunks, content, maxChunkSize);
  }
  
  private static class CoreChunk {
    final int startOffset;
    final int endOffset;
    final TextChunk.ChunkType type;
    
    CoreChunk(int startOffset, int endOffset, TextChunk.ChunkType type) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.type = type;
    }
  }
  
  private void traverseAndCollectCoreChunks(Node node, String content, List<String> chunkNodeTypes, 
                                           int maxChunkSize, List<CoreChunk> coreChunks) {
    var nodeType = node.getType();
    
    // Check if this is a node type we want to extract as a chunk
    if (chunkNodeTypes.contains(nodeType)) {
      var startByte = node.getStartByte();
      var endByte = node.getEndByte();
      var nodeText = content.substring(startByte, endByte);
      
      // Calculate space needed for context and ellipses
      var contextOverhead = calculateContextOverhead(startByte, endByte, content.length());
      
      if (nodeText.length() + contextOverhead <= maxChunkSize) {
        // Create core chunk for this node
        var chunkType = mapNodeTypeToChunkType(nodeType);
        coreChunks.add(new CoreChunk(startByte, endByte, chunkType));
        return; // Don't traverse children if we've added this node as a chunk
      } else {
        // Node too large, try to split it by traversing children
        splitLargeNode(node, content, chunkNodeTypes, maxChunkSize, coreChunks);
        return;
      }
    }
    
    // Traverse children
    for (int i = 0; i < node.getChildCount(); i++) {
      var child = node.getChild(i);
      traverseAndCollectCoreChunks(child, content, chunkNodeTypes, maxChunkSize, coreChunks);
    }
  }
  
  private void splitLargeNode(Node node, String content, List<String> chunkNodeTypes, 
                             int maxChunkSize, List<CoreChunk> coreChunks) {
    // Try to find smaller chunks within this large node
    boolean foundChildChunks = false;
    
    for (int i = 0; i < node.getChildCount(); i++) {
      var child = node.getChild(i);
      var childStartByte = child.getStartByte();
      var childEndByte = child.getEndByte();
      var childText = content.substring(childStartByte, childEndByte);
      var contextOverhead = calculateContextOverhead(childStartByte, childEndByte, content.length());
      
      if (childText.length() + contextOverhead <= maxChunkSize && chunkNodeTypes.contains(child.getType())) {
        var chunkType = mapNodeTypeToChunkType(child.getType());
        coreChunks.add(new CoreChunk(childStartByte, childEndByte, chunkType));
        foundChildChunks = true;
      } else {
        // Recursively try children
        traverseAndCollectCoreChunks(child, content, chunkNodeTypes, maxChunkSize, coreChunks);
      }
    }
    
    // If no child chunks found, create text-based chunks for this node
    if (!foundChildChunks) {
      var startByte = node.getStartByte();
      var endByte = node.getEndByte();
      var nodeText = content.substring(startByte, endByte);
      var textCoreChunks = createTextCoreChunks(nodeText, startByte, maxChunkSize);
      coreChunks.addAll(textCoreChunks);
    }
  }
  
  private int calculateContextOverhead(int startOffset, int endOffset, int contentLength) {
    var overhead = 0;
    
    // Add ellipsis overhead if not at beginning
    if (startOffset > 0) {
      overhead += ELLIPSIS.length() + 1; // +1 for newline
    }
    
    // Add ellipsis overhead if not at end
    if (endOffset < contentLength) {
      overhead += ELLIPSIS.length() + 1; // +1 for newline
    }
    
    // Add context buffer overhead
    if (startOffset > 0) {
      overhead += Math.min(CONTEXT_BUFFER_SIZE, startOffset);
    }
    if (endOffset < contentLength) {
      overhead += Math.min(CONTEXT_BUFFER_SIZE, contentLength - endOffset);
    }
    
    return overhead;
  }
  
  private List<CoreChunk> fillGaps(List<CoreChunk> coreChunks, String content, int maxChunkSize) {
    var completeCoreChunks = new ArrayList<CoreChunk>();
    var currentPos = 0;
    
    for (var chunk : coreChunks) {
      // Fill gap before this chunk
      if (currentPos < chunk.startOffset) {
        var gapText = content.substring(currentPos, chunk.startOffset).trim();
        if (!gapText.isEmpty()) {
          var gapChunks = createTextCoreChunks(gapText, currentPos, maxChunkSize);
          completeCoreChunks.addAll(gapChunks);
        }
      }
      
      completeCoreChunks.add(chunk);
      currentPos = Math.max(currentPos, chunk.endOffset);
    }
    
    // Fill gap after last chunk
    if (currentPos < content.length()) {
      var gapText = content.substring(currentPos).trim();
      if (!gapText.isEmpty()) {
        var gapChunks = createTextCoreChunks(gapText, currentPos, maxChunkSize);
        completeCoreChunks.addAll(gapChunks);
      }
    }
    
    return completeCoreChunks;
  }
  
  private List<CoreChunk> createTextCoreChunks(String text, int baseOffset, int maxChunkSize) {
    var chunks = new ArrayList<CoreChunk>();
    var lines = text.split("\n");
    var currentChunk = new StringBuilder();
    var chunkStartOffset = baseOffset;
    var currentOffset = baseOffset;
    
    for (var line : lines) {
      var lineWithNewline = line + "\n";
      var contextOverhead = calculateContextOverhead(chunkStartOffset, 
                                                   currentOffset + lineWithNewline.length(), 
                                                   baseOffset + text.length());
      
      if (currentChunk.length() + lineWithNewline.length() + contextOverhead > maxChunkSize && currentChunk.length() > 0) {
        // Current chunk would exceed limit, save it and start new one
        chunks.add(new CoreChunk(chunkStartOffset, currentOffset, TextChunk.ChunkType.TEXT));
        currentChunk = new StringBuilder();
        chunkStartOffset = currentOffset;
      }
      
      currentChunk.append(lineWithNewline);
      currentOffset += lineWithNewline.length();
    }
    
    // Add the last chunk if it has content
    if (currentChunk.length() > 0) {
      chunks.add(new CoreChunk(chunkStartOffset, currentOffset, TextChunk.ChunkType.TEXT));
    }
    
    return chunks;
  }
  
  private List<TextChunk> addContextToChunks(List<CoreChunk> coreChunks, String content, int maxChunkSize) {
    var chunks = new ArrayList<TextChunk>();
    
    for (var coreChunk : coreChunks) {
      var chunkContent = buildChunkWithContext(coreChunk, content, maxChunkSize);
      
      // Calculate the actual start/end offsets of the complete chunk (including context)
      var contextStart = Math.max(0, coreChunk.startOffset - CONTEXT_BUFFER_SIZE);
      var contextEnd = Math.min(content.length(), coreChunk.endOffset + CONTEXT_BUFFER_SIZE);
      
      chunks.add(new TextChunk(contextStart, contextEnd, chunkContent, coreChunk.type, 
                              coreChunk.startOffset, coreChunk.endOffset));
    }
    
    return chunks;
  }
  
  private String buildChunkWithContext(CoreChunk coreChunk, String content, int maxChunkSize) {
    var result = new StringBuilder();
    var coreContent = content.substring(coreChunk.startOffset, coreChunk.endOffset);
    
    // Add context before
    if (coreChunk.startOffset > 0) {
      result.append(ELLIPSIS).append("\n");
      var contextStart = Math.max(0, coreChunk.startOffset - CONTEXT_BUFFER_SIZE);
      var contextBefore = content.substring(contextStart, coreChunk.startOffset);
      result.append(contextBefore);
    }
    
    // Add core content
    result.append(coreContent);
    
    // Add context after
    if (coreChunk.endOffset < content.length()) {
      var contextEnd = Math.min(content.length(), coreChunk.endOffset + CONTEXT_BUFFER_SIZE);
      var contextAfter = content.substring(coreChunk.endOffset, contextEnd);
      result.append(contextAfter);
      result.append(ELLIPSIS).append("\n");
    }
    
    // Trim to fit within maxChunkSize if necessary
    var resultStr = result.toString();
    if (resultStr.length() > maxChunkSize) {
      resultStr = resultStr.substring(0, maxChunkSize - 3) + "...";
    }
    
    return resultStr;
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
    var coreChunks = createTextCoreChunks(content, 0, maxChunkSize);
    return addContextToChunks(coreChunks, content, maxChunkSize);
  }
}
