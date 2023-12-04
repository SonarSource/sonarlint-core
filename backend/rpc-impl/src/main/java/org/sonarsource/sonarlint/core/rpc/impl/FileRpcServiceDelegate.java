/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetFilesStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetFilesStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetPathTranslationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetPathTranslationResponse;

import static org.apache.commons.io.FilenameUtils.separatorsToUnix;

public class FileRpcServiceDelegate extends AbstractRpcServiceDelegate implements FileRpcService {
  public static final GetPathTranslationResponse EMPTY_RESPONSE = new GetPathTranslationResponse(null, null);

  protected FileRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<GetPathTranslationResponse> getPathTranslation(GetPathTranslationParams params) {
    return requestAsync(cancelChecker -> {
      var translation = getBean(PathTranslationService.class).getPathTranslation(params.getConfigurationScopeId());
      return translation.map(filePathTranslation -> new GetPathTranslationResponse(
        separatorsToUnix(filePathTranslation.getIdePathPrefix().toString()),
        separatorsToUnix(filePathTranslation.getServerPathPrefix().toString())))
        .orElse(EMPTY_RESPONSE);
    });
  }

  @Override
  public CompletableFuture<GetFilesStatusResponse> getFilesStatus(GetFilesStatusParams params) {
    return requestAsync(cancelChecker -> {
      var statuses = getBean(FileExclusionService.class).getFilesStatus(params.getFileUrisByConfigScopeId());
      return new GetFilesStatusResponse(statuses);
    });
  }

  @Override
  public void didUpdateFileSystem(DidUpdateFileSystemParams params) {
    notify(() -> getBean(ClientFileSystemService.class).didUpdateFileSystem(params));
  }
}
