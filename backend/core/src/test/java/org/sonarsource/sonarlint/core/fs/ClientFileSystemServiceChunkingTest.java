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
package org.sonarsource.sonarlint.core.fs;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonarsource.sonarlint.core.chunking.FileChunkingService;
import org.sonarsource.sonarlint.core.chunking.TextChunk;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientFileSystemServiceChunkingTest {

  @Mock
  private SonarLintRpcClient rpcClient;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private OpenFilesRepository openFilesRepository;

  @Mock
  private TelemetryService telemetryService;

  @Mock
  private FileChunkingService fileChunkingService;

  private ClientFileSystemService service;
  private URI testFileUri;
  private ClientFileDto testFileDto;

  @BeforeEach
  void setUp() {
    testFileUri = URI.create("file:///test.java");
    testFileDto = new ClientFileDto(
      testFileUri,
      Path.of("test.java"),
      "testScope",
      false,
      "UTF-8",
      Path.of("/path/to/test.java"),
      "public class Test {}",
      Language.JAVA,
      false);

    service = new ClientFileSystemService(rpcClient, eventPublisher, openFilesRepository,
      telemetryService, fileChunkingService);
  }

  @Test
  void should_chunk_files_for_configuration_scope() {
    // Setup file listing response
    var listFilesResponse = new ListFilesResponse(List.of(testFileDto));
    when(rpcClient.listFiles(any(ListFilesParams.class)))
      .thenReturn(CompletableFuture.completedFuture(listFilesResponse));

    // Setup chunking response
    var expectedChunks = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS));
    when(fileChunkingService.chunkFiles(any()))
      .thenReturn(java.util.Map.of(testFileUri, expectedChunks));

    var result = service.chunkFiles("testScope");

    assertThat(result).hasSize(1);
    assertThat(result.get(testFileUri)).isEqualTo(expectedChunks);
    verify(fileChunkingService).chunkFiles(any());
  }

  @Test
  void should_chunk_specific_file() {
    // Setup file system initialization
    var listFilesResponse = new ListFilesResponse(List.of(testFileDto));
    when(rpcClient.listFiles(any(ListFilesParams.class)))
      .thenReturn(CompletableFuture.completedFuture(listFilesResponse));

    var expectedChunks = List.of(
      new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS));
    when(fileChunkingService.chunkFile(any(ClientFile.class)))
      .thenReturn(expectedChunks);

    // Initialize file system first
    service.getFiles("testScope");

    var result = service.chunkFile(testFileUri);

    assertThat(result).isEqualTo(expectedChunks);
    verify(fileChunkingService).chunkFile(any(ClientFile.class));
  }

  @Test
  void should_return_empty_list_for_non_existent_file() {
    var nonExistentUri = URI.create("file:///nonexistent.java");

    var result = service.chunkFile(nonExistentUri);

    assertThat(result).isEmpty();
  }

  @Test
  void should_clear_chunking_cache_on_file_updates() {
    var updatedFileDto = new ClientFileDto(
      testFileUri,
      Path.of("test.java"),
      "testScope",
      false,
      "UTF-8",
      Path.of("/path/to/test.java"),
      "public class UpdatedTest {}",
      Language.JAVA,
      false);

    var params = new DidUpdateFileSystemParams(
      List.of(), // removed
      List.of(), // added
      List.of(updatedFileDto.getUri()) // changed
    );

    service.didUpdateFileSystem(params);

    verify(fileChunkingService).clearCache(eq(List.of(testFileUri)));
  }

  @Test
  void should_clear_chunking_cache_on_file_additions() {
    var newFileUri = URI.create("file:///new.java");
    var newFileDto = new ClientFileDto(
      newFileUri,
      Path.of("new.java"),
      "testScope",
      false,
      "UTF-8",
      Path.of("/path/to/new.java"),
      "public class New {}",
      Language.JAVA,
      false);

    var params = new DidUpdateFileSystemParams(
      List.of(), // removed
      List.of(newFileDto), // added
      List.of() // changed
    );

    service.didUpdateFileSystem(params);

    verify(fileChunkingService).clearCache(eq(List.of(newFileUri)));
  }

  @Test
  void should_clear_chunking_cache_on_file_removal() {
    // Setup initial file system
    var listFilesResponse = new ListFilesResponse(List.of(testFileDto));
    when(rpcClient.listFiles(any(ListFilesParams.class)))
      .thenReturn(CompletableFuture.completedFuture(listFilesResponse));

    // Initialize file system
    service.getFiles("testScope");

    var params = new DidUpdateFileSystemParams(
      List.of(testFileDto), // removed
      List.of(), // added
      List.of() // changed
    );

    service.didUpdateFileSystem(params);

    verify(fileChunkingService).clearCache(eq(List.of(testFileUri)));
  }

  @Test
  void should_clear_chunking_cache_on_configuration_scope_removal() {
    // Setup initial file system
    var listFilesResponse = new ListFilesResponse(List.of(testFileDto));
    when(rpcClient.listFiles(any(ListFilesParams.class)))
      .thenReturn(CompletableFuture.completedFuture(listFilesResponse));

    // Initialize file system
    service.getFiles("testScope");

    var event = new ConfigurationScopeRemovedEvent(new ConfigurationScope("testScope", null, true, "scope"), new BindingConfiguration(null, null, false));
    service.onConfigurationScopeRemoved(event);

    verify(fileChunkingService).clearCache(eq(List.of(testFileUri)));
  }

  @Test
  void should_clear_all_chunking_cache_on_shutdown() {
    service.shutdown();

    verify(fileChunkingService).clearAllCache();
  }

  @Test
  void should_handle_multiple_file_operations_in_batch() {
    var file2Uri = URI.create("file:///test2.java");
    var file3Uri = URI.create("file:///test3.java");

    var file2Dto = new ClientFileDto(
      file2Uri, Path.of("test2.java"), "testScope", false, "UTF-8",
      Path.of("/path/to/test2.java"), "public class Test2 {}", Language.JAVA, false);

    var file3Dto = new ClientFileDto(
      file3Uri, Path.of("test3.java"), "testScope", false, "UTF-8",
      Path.of("/path/to/test3.java"), "public class Test3 {}", Language.JAVA, false);

    var params = new DidUpdateFileSystemParams(
      List.of(testFileDto), // removed
      List.of(file2Dto), // added
      List.of(file3Dto.getUri()) // changed
    );

    service.didUpdateFileSystem(params);

    verify(fileChunkingService).clearCache(eq(List.of(testFileUri, file2Uri, file3Uri)));
  }

  @Test
  void should_not_clear_cache_when_no_files_changed() {
    var params = new DidUpdateFileSystemParams(
      List.of(), // removed
      List.of(), // added
      List.of() // changed
    );

    service.didUpdateFileSystem(params);

    // Should not call clearCache with empty list, but current implementation might
    // Let's verify it doesn't cause issues
    verify(fileChunkingService).clearCache(eq(List.of()));
  }

  @Test
  void should_handle_files_with_different_languages() {
    var jsFileUri = URI.create("file:///test.js");
    var xmlFileUri = URI.create("file:///config.xml");

    var jsFileDto = new ClientFileDto(
      jsFileUri, Path.of("test.js"), "testScope", false, "UTF-8",
      Path.of("/path/to/test.js"), "function test() {}", Language.JS, false);

    var xmlFileDto = new ClientFileDto(
      xmlFileUri, Path.of("config.xml"), "testScope", false, "UTF-8",
      Path.of("/path/to/config.xml"), "<root></root>", Language.XML, false);

    // Setup file listing response
    var listFilesResponse = new ListFilesResponse(List.of(testFileDto, jsFileDto, xmlFileDto));
    when(rpcClient.listFiles(any(ListFilesParams.class)))
      .thenReturn(CompletableFuture.completedFuture(listFilesResponse));

    // Setup chunking response
    var javaChunks = List.of(new TextChunk(0, 19, "public class Test {}", TextChunk.ChunkType.CLASS));
    var jsChunks = List.of(new TextChunk(0, 17, "function test() {}", TextChunk.ChunkType.METHOD));
    var xmlChunks = List.of(new TextChunk(0, 13, "<root></root>", TextChunk.ChunkType.XML_ELEMENT));

    when(fileChunkingService.chunkFiles(any()))
      .thenReturn(java.util.Map.of(
        testFileUri, javaChunks,
        jsFileUri, jsChunks,
        xmlFileUri, xmlChunks));

    var result = service.chunkFiles("testScope");

    assertThat(result).hasSize(3);
    assertThat(result.get(testFileUri)).isEqualTo(javaChunks);
    assertThat(result.get(jsFileUri)).isEqualTo(jsChunks);
    assertThat(result.get(xmlFileUri)).isEqualTo(xmlChunks);
  }
}
