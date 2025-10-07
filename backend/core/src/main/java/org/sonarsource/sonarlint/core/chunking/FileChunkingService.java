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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.fs.ClientFile;

/**
 * Service responsible for chunking files into meaningful text segments for semantic search.
 */
@Named
@Singleton
public class FileChunkingService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final int DEFAULT_MAX_CHUNK_SIZE = 512;
  
  private final CodeChunker codeChunker;
  private final Map<URI, List<TextChunk>> chunkCache = new ConcurrentHashMap<>();
  private final int maxChunkSize;
  private final ChunkingStrategy defaultStrategy;

  public FileChunkingService() {
    this(new TreeSitterCodeChunker(), DEFAULT_MAX_CHUNK_SIZE, ChunkingStrategy.LARGEST_AST_NODE);
  }
  
  public FileChunkingService(CodeChunker codeChunker, int maxChunkSize) {
    this(codeChunker, maxChunkSize, ChunkingStrategy.LARGEST_AST_NODE);
  }
  
  public FileChunkingService(CodeChunker codeChunker, int maxChunkSize, ChunkingStrategy defaultStrategy) {
    this.codeChunker = codeChunker;
    this.maxChunkSize = maxChunkSize;
    this.defaultStrategy = defaultStrategy;
  }

  /**
   * Chunks multiple files and returns a map of URI to list of chunks using the default strategy.
   *
   * @param files The files to chunk
   * @return Map where key is file URI and value is list of chunks for that file
   */
  public Map<URI, List<TextChunk>> chunkFiles(List<ClientFile> files) {
    return chunkFiles(files, defaultStrategy);
  }
  
  /**
   * Chunks multiple files and returns a map of URI to list of chunks using the specified strategy.
   *
   * @param files The files to chunk
   * @param strategy The chunking strategy to use
   * @return Map where key is file URI and value is list of chunks for that file
   */
  public Map<URI, List<TextChunk>> chunkFiles(List<ClientFile> files, ChunkingStrategy strategy) {
    var result = new HashMap<URI, List<TextChunk>>();
    
    for (var file : files) {
      try {
        var chunks = chunkFile(file, strategy);
        if (!chunks.isEmpty()) {
          result.put(file.getUri(), chunks);
          LOG.debug("Chunked file {} into {} chunks using strategy {}", file.getUri(), chunks.size(), strategy);
        }
      } catch (Exception e) {
        LOG.error("Failed to chunk file {}", file.getUri(), e);
      }
    }
    
    return result;
  }

  /**
   * Chunks a single file into text segments using the default strategy.
   *
   * @param file The file to chunk
   * @return List of text chunks
   */
  public List<TextChunk> chunkFile(ClientFile file) {
    return chunkFile(file, defaultStrategy);
  }

  /**
   * Chunks a single file into text segments using the specified strategy.
   *
   * @param file The file to chunk
   * @param strategy The chunking strategy to use
   * @return List of text chunks
   */
  public List<TextChunk> chunkFile(ClientFile file, ChunkingStrategy strategy) {
    // Check cache first (cache key includes strategy)
    var cacheKey = file.getUri();
    var cached = chunkCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    try {
      var content = file.getContent();
      if (content == null || content.trim().isEmpty()) {
        return List.of();
      }

      var language = file.getDetectedLanguage();
      if (language == null) {
        language = detectLanguageFromExtension(file.getFileName());
      }

      List<TextChunk> chunks;
      if (language != null && codeChunker.getSupportedLanguages().contains(language)) {
        try {
          chunks = codeChunker.chunk(content, language, maxChunkSize, strategy);
        } catch (Exception e) {
          LOG.warn("Error chunking file {} with language {}, falling back to text chunking: {}", 
                   file.getUri(), language, e.getMessage());
          chunks = createFallbackChunks(content, strategy);
        }
      } else {
        LOG.debug("Language {} not supported or detected for file {}, using fallback chunking", 
                 language, file.getUri());
        chunks = createFallbackChunks(content, strategy);
      }

      // Cache the result
      chunkCache.put(cacheKey, chunks);
      
      return chunks;
    } catch (Exception e) {
      LOG.error("Error chunking file {}", file.getUri(), e);
      return List.of();
    }
  }

  /**
   * Clears the chunk cache for specific files.
   *
   * @param fileUris URIs of files to clear from cache
   */
  public void clearCache(List<URI> fileUris) {
    fileUris.forEach(chunkCache::remove);
  }

  /**
   * Clears the entire chunk cache.
   */
  public void clearAllCache() {
    chunkCache.clear();
  }

  /**
   * Gets cached chunks for a file without computing them.
   *
   * @param fileUri The file URI
   * @return Cached chunks or null if not cached
   */
  @CheckForNull
  public List<TextChunk> getCachedChunks(URI fileUri) {
    return chunkCache.get(fileUri);
  }

  /**
   * Returns statistics about the chunking cache.
   *
   * @return Cache statistics
   */
  public ChunkingStats getStats() {
    var totalChunks = chunkCache.values().stream()
        .mapToInt(List::size)
        .sum();
    
    return new ChunkingStats(chunkCache.size(), totalChunks, maxChunkSize);
  }

  @CheckForNull
  private SonarLanguage detectLanguageFromExtension(String fileName) {
    var extension = getFileExtension(fileName);
    if (extension == null) {
      return null;
    }

    return switch (extension.toLowerCase()) {
      case "java" -> SonarLanguage.JAVA;
      case "js", "jsx" -> SonarLanguage.JS;
      case "xml" -> SonarLanguage.XML;
      case "html", "htm" -> SonarLanguage.HTML;
      case "json" -> SonarLanguage.JSON;
      case "yml", "yaml" -> SonarLanguage.YAML;
      default -> null;
    };
  }

  @CheckForNull
  private String getFileExtension(String fileName) {
    var lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
      return null;
    }
    return fileName.substring(lastDotIndex + 1);
  }

  private List<TextChunk> createFallbackChunks(String content, ChunkingStrategy strategy) {
    if (strategy == ChunkingStrategy.WHOLE_FILE) {
      return List.of(new TextChunk(0, content.length(), content, TextChunk.ChunkType.TEXT, 0, content.length()));
    }
    
    // Use context-aware text chunking for LARGEST_AST_NODE strategy
    return createContextAwareTextChunks(content);
  }
  
  private List<TextChunk> createContextAwareTextChunks(String content) {
    var chunks = new ArrayList<TextChunk>();
    var lines = content.split("\n", -1); // Keep empty strings to preserve structure
    var currentChunk = new StringBuilder();
    var chunkStartOffset = 0;
    var currentOffset = 0;
    
    for (int i = 0; i < lines.length; i++) {
      var line = lines[i];
      var isLastLine = (i == lines.length - 1);
      var lineWithNewline = isLastLine ? line : line + "\n";
      
      var estimatedContextSize = estimateContextOverhead(chunkStartOffset, 
                                                        currentOffset + lineWithNewline.length(), 
                                                        content.length());
      
      if (currentChunk.length() + lineWithNewline.length() + estimatedContextSize > maxChunkSize && currentChunk.length() > 0) {
        // Add context-aware chunk
        var chunkContent = buildContextAwareChunk(content, chunkStartOffset, currentOffset);
        var contextStart = Math.max(0, chunkStartOffset - 50);
        var contextEnd = Math.min(content.length(), currentOffset + 50);
        
        chunks.add(new TextChunk(contextStart, contextEnd, chunkContent, TextChunk.ChunkType.TEXT, 
                                chunkStartOffset, currentOffset));
        
        currentChunk = new StringBuilder();
        chunkStartOffset = currentOffset;
      }
      
      currentChunk.append(lineWithNewline);
      currentOffset += lineWithNewline.length();
    }
    
    // Add the last chunk if it has content
    if (currentChunk.length() > 0) {
      var chunkContent = buildContextAwareChunk(content, chunkStartOffset, currentOffset);
      var contextStart = Math.max(0, chunkStartOffset - 50);
      var contextEnd = Math.min(content.length(), currentOffset + 50);
      
      chunks.add(new TextChunk(contextStart, contextEnd, chunkContent, TextChunk.ChunkType.TEXT,
                              chunkStartOffset, currentOffset));
    }
    
    return chunks;
  }
  
  private int estimateContextOverhead(int startOffset, int endOffset, int contentLength) {
    var overhead = 0;
    
    // Add ellipsis overhead if not at beginning/end
    if (startOffset > 0) overhead += 4; // "...\n"
    if (endOffset < contentLength) overhead += 4; // "...\n"
    
    // Add context buffer overhead
    if (startOffset > 0) overhead += Math.min(50, startOffset);
    if (endOffset < contentLength) overhead += Math.min(50, contentLength - endOffset);
    
    return overhead;
  }
  
  private String buildContextAwareChunk(String content, int coreStart, int coreEnd) {
    var result = new StringBuilder();
    
    // Add context before
    if (coreStart > 0) {
      result.append("...\n");
      var contextStart = Math.max(0, coreStart - 50);
      var contextBefore = content.substring(contextStart, coreStart);
      result.append(contextBefore);
    }
    
    // Add core content
    result.append(content.substring(coreStart, coreEnd));
    
    // Add context after
    if (coreEnd < content.length()) {
      var contextEnd = Math.min(content.length(), coreEnd + 50);
      var contextAfter = content.substring(coreEnd, contextEnd);
      result.append(contextAfter);
      result.append("...\n");
    }
    
    // Trim to fit within maxChunkSize if necessary
    var resultStr = result.toString();
    if (resultStr.length() > maxChunkSize) {
      resultStr = resultStr.substring(0, maxChunkSize - 3) + "...";
    }
    
    return resultStr;
  }

  /**
   * Statistics about the chunking service.
   */
  public static class ChunkingStats {
    private final int cachedFiles;
    private final int totalChunks;
    private final int maxChunkSize;

    public ChunkingStats(int cachedFiles, int totalChunks, int maxChunkSize) {
      this.cachedFiles = cachedFiles;
      this.totalChunks = totalChunks;
      this.maxChunkSize = maxChunkSize;
    }

    public int getCachedFiles() {
      return cachedFiles;
    }

    public int getTotalChunks() {
      return totalChunks;
    }

    public int getMaxChunkSize() {
      return maxChunkSize;
    }

    @Override
    public String toString() {
      return String.format("ChunkingStats{cachedFiles=%d, totalChunks=%d, maxChunkSize=%d}",
                          cachedFiles, totalChunks, maxChunkSize);
    }
  }
}
