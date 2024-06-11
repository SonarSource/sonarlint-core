/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.grip;

import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.grip.web.api.GripWebApi;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixParams;

@Named
@Singleton
public class GripService {
  private final GripWebApi gripWebApi;
  private final ClientFileSystemService fileSystemService;

  public GripService(HttpClientProvider httpClientProvider, ClientFileSystemService fileSystemService) {
    this.gripWebApi = new GripWebApi(httpClientProvider);
    this.fileSystemService = fileSystemService;
  }

  public String suggestFix(SuggestFixParams params) {
    var fileUri = params.getFileUri();
    var clientFile = fileSystemService.getClientFiles(params.getConfigurationScopeId(), fileUri);
    if (clientFile == null) {
      throw new IllegalStateException("Cannot find the file with URI: " + fileUri);
    }
    var response = gripWebApi.suggestFix(params, clientFile.getContent());
    return response.choices.get(response.choices.size() - 1).message.content;
  }
}
