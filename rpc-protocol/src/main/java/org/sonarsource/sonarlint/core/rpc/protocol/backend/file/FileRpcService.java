/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.file;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

@JsonSegment("file")
public interface FileRpcService {

  /**
   * Returns whether a file is currently excluded or not
   * It is based on the same criteria as for the exclusions during an analysis
   */
  @JsonRequest
  CompletableFuture<GetFilesStatusResponse> getFilesStatus(GetFilesStatusParams params);

  @JsonNotification
  void didUpdateFileSystem(DidUpdateFileSystemParams params);

  /**
   * Should be called by clients when a file has been opened in the editor.
   */
  @JsonNotification
  void didOpenFile(DidOpenFileParams params);

  /**
   * Should be called by clients when a file has been closed in the editor.
   */
  @JsonNotification
  void didCloseFile(DidCloseFileParams params);

  /**
   * Submit a chunk of files to the backend file system cache.
   * Used during asynchronous cache warmup initiated by the backend.
   * @since 10.12
   */
  @JsonNotification
  void submitFileCacheChunk(SubmitFileCacheChunkParams params);
}
