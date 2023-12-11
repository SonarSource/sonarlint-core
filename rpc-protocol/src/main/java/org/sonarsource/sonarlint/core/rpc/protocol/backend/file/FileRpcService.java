/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2023 SonarSource SA
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
   * Users can open a different folder than the one analyzed on SonarCloud/SonarQube, e.g. a subproject.
   * As a consequence, relative file paths on the client side might differ from server relative file paths.
   * The process called 'path matching' consists in identifying what are the IDE and server path prefixes,
   * that can be used to translate one path to another.
   */
  @JsonRequest
  CompletableFuture<GetPathTranslationResponse> getPathTranslation(GetPathTranslationParams params);

  @JsonRequest
  CompletableFuture<GetFilesStatusResponse> getFilesStatus(GetFilesStatusParams params);

  @JsonNotification
  void didUpdateFileSystem(DidUpdateFileSystemParams params);
}
