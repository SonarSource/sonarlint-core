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

  public FileChunkingService() {
    this(new TreeSitterCodeChunker(), DEFAULT_MAX_CHUNK_SIZE);
  }
  
  public FileChunkingService(CodeChunker codeChunker, int maxChunkSize) {
    this.codeChunker = codeChunker;
    this.maxChunkSize = maxChunkSize;
  }

  /**
   * Chunks multiple files and returns a map of URI to list of chunks.
   *
   * @param files The files to chunk
   * @return Map where key is file URI and value is list of chunks for that file
   */
  public Map<URI, List<TextChunk>> chunkFiles(List<ClientFile> files) {
    var result = new HashMap<URI, List<TextChunk>>();
    
    for (var file : files) {
      try {
        var chunks = chunkFile(file);
        if (!chunks.isEmpty()) {
          result.put(file.getUri(), chunks);
          LOG.debug("Chunked file {} into {} chunks", file.getUri(), chunks.size());
        }
      } catch (Exception e) {
        LOG.error("Failed to chunk file {}", file.getUri(), e);
      }
    }
    
    return result;
  }

  /**
   * Chunks a single file into text segments.
   *
   * @param file The file to chunk
   * @return List of text chunks
   */
  public List<TextChunk> chunkFile(ClientFile file) {
    // Check cache first
    var cached = chunkCache.get(file.getUri());
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
        chunks = codeChunker.chunk(content, language, maxChunkSize);
      } else {
        LOG.debug("Language {} not supported or detected for file {}, using fallback chunking", 
                 language, file.getUri());
        chunks = createFallbackChunks(content);
      }

      // Cache the result
      chunkCache.put(file.getUri(), chunks);
      
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

  private List<TextChunk> createFallbackChunks(String content) {
    var chunks = new ArrayList<TextChunk>();
    var lines = content.split("\n");
    var currentChunk = new StringBuilder();
    var chunkStartOffset = 0;
    var currentOffset = 0;
    
    for (var line : lines) {
      var lineWithNewline = line + "\n";
      
      if (currentChunk.length() + lineWithNewline.length() > maxChunkSize && currentChunk.length() > 0) {
        // Current chunk would exceed limit, save it and start new one
        chunks.add(new TextChunk(chunkStartOffset, currentOffset, 
                                currentChunk.toString().trim(), TextChunk.ChunkType.TEXT));
        currentChunk = new StringBuilder();
        chunkStartOffset = currentOffset;
      }
      
      currentChunk.append(lineWithNewline);
      currentOffset += lineWithNewline.length();
    }
    
    // Add the last chunk if it has content
    if (currentChunk.length() > 0) {
      chunks.add(new TextChunk(chunkStartOffset, currentOffset, 
                              currentChunk.toString().trim(), TextChunk.ChunkType.TEXT));
    }
    
    return chunks;
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
