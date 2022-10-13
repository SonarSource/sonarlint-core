/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.system.ServerInfo;
import org.sonarsource.sonarlint.core.serverapi.system.SystemApi;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerPathProviderTests {

  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  @Test
  void old_token_generation_path_for_sonar_cloud() {
    var serverPath = ServerPathProvider.buildServerPath("baseUrl", "5.1", 1234, "My IDE", true);

    assertThat(serverPath).isEqualTo("baseUrl/account/security");
  }

  @Test
  void new_auth_path_for_9_7_version() {
    var serverPath = ServerPathProvider.buildServerPath("baseUrl", "9.7", 1234, "My IDE", false);

    assertThat(serverPath).isEqualTo("baseUrl/sonarlint/auth?ideName=My+IDE&port=1234");
  }

  @Test
  void new_auth_path_for_version_greater_than_9_7() {
    var serverPath = ServerPathProvider.buildServerPath("baseUrl", "9.8", 1234, "My IDE", false);

    assertThat(serverPath).isEqualTo("baseUrl/sonarlint/auth?ideName=My+IDE&port=1234");
  }

  @Test
  void profile_token_generation_path_for_version_lower_than_9_7() {
    var serverPath = ServerPathProvider.buildServerPath("baseUrl", "9.6", 1234, "My IDE", false);

    assertThat(serverPath).isEqualTo("baseUrl/account/security");
  }

  @Test
  void profile_token_generation_for_not_started_loopback_server() {
    var serverPath = ServerPathProvider.buildServerPath("baseUrl", "9.7", -1, "My IDE", false);

    assertThat(serverPath).isEqualTo("baseUrl/sonarlint/auth?ideName=My+IDE");
  }

  @Test
  void should_provide_token_generation_path_for_base_server_url() {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.6\", \"id\": \"xzy\"}");
    var client = MockWebServerExtension.httpClient();
    var baseUrl = mockWebServerExtension.url("");

    var serverUrl = ServerPathProvider.getServerUrlForTokenGeneration(mockWebServerExtension.endpointParams(), client, 1234, "My IDE");

    assertThat(serverUrl).isEqualTo(baseUrl + "/account/security");
  }

  @Test
  void should_throw_download_exception_on_malformed_json() {
    mockWebServerExtension.addStringResponse("/api/system/status", "not a json");
    var client = MockWebServerExtension.httpClient();
    var baseUrl = mockWebServerExtension.url("");
    var endpointParams = mockWebServerExtension.endpointParams();

    assertThatThrownBy(() ->
      ServerPathProvider.getServerUrlForTokenGeneration(endpointParams, client, 1234, "My IDE"))
      .isInstanceOf(DownloadException.class)
      .hasMessage("Failed to get server status for " + baseUrl + " due to malformed response");
  }

  @Test
  void should_rethrow_download_exception_on_thread_interrupted_exception() throws Exception {
    var serverApi = mock(ServerApi.class);
    var systemApi = mock(SystemApi.class);
    var statusFuture = mock(CompletableFuture.class);
    when(serverApi.system()).thenReturn(systemApi);
    when(systemApi.getStatus()).thenReturn(statusFuture);
    when(statusFuture.get()).thenThrow(InterruptedException.class);

    var baseUrl = mockWebServerExtension.url("");
    var endpointParams = mockWebServerExtension.endpointParams();

    mockWebServerExtension.shutdownServer();
    assertThatThrownBy(() -> ServerPathProvider.getServerInfo(endpointParams, serverApi))
      .isInstanceOf(DownloadException.class)
      .hasMessage("Failed to get server status for " + baseUrl + " due to thread interruption");
  }

}
