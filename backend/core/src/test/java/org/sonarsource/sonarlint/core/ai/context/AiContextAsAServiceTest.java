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
package org.sonarsource.sonarlint.core.ai.context;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.fs.FileSystemInitialized;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiContextAsAServiceTest {

  private HttpClient httpClient;
  private AiContextAsAService aiContextAsAService;

  @BeforeEach
  void prepare() {
    HttpClientProvider httpClientProvider = mock(HttpClientProvider.class);
    httpClient = mock(HttpClient.class);
    when(httpClientProvider.getHttpClient()).thenReturn(httpClient);
    when(httpClient.postAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    aiContextAsAService = new AiContextAsAService(httpClientProvider);
  }

  @Test
  void should_send_empty_index_request_if_no_file() {
    aiContextAsAService.onFileSystemInitialized(new FileSystemInitialized(List.of()));

    verify(httpClient).postAsync("http://localhost:8080/index", "application/json; charset=utf-8", "{\"files\":[]}");
  }

  @Test
  void should_send_index_request_with_single_file() {
    aiContextAsAService.onFileSystemInitialized(new FileSystemInitialized(
      List.of(new ClientFileDto(URI.create("file://path/file.js"), Paths.get("path/file.js"), "configScope", true, null, null, "content", Language.ABAP, false))));

    verify(httpClient).postAsync("http://localhost:8080/index", "application/json; charset=utf-8", "{\"files\":[{\"content\":\"content\",\"fileRelativePath\":\"path/file.js\",\"metadata\":{\"language\":\"ABAP\"}}]}");
  }

}
