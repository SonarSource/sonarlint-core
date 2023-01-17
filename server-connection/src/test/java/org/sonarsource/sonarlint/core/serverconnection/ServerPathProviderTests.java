/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

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
  void should_provide_token_generation_path_for_base_server_url() throws ExecutionException, InterruptedException {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.6\", \"id\": \"xzy\"}");
    var client = MockWebServerExtension.httpClient();
    var baseUrl = mockWebServerExtension.url("");

    var serverUrl = ServerPathProvider.getServerUrlForTokenGeneration(mockWebServerExtension.endpointParams(), client, 1234, "My IDE").get();

    assertThat(serverUrl).isEqualTo(baseUrl + "account/security");
  }

  @Test
  void should_provide_token_generation_fallback_path_for_base_server_url() throws ExecutionException, InterruptedException {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.6\", \"id\": \"xzy\"}");
    var client = MockWebServerExtension.httpClient();
    var baseUrl = mockWebServerExtension.url("");

    var serverUrl = ServerPathProvider.getFallbackServerUrlForTokenGeneration(mockWebServerExtension.endpointParams(), client, "My IDE").get();

    assertThat(serverUrl).isEqualTo(baseUrl + "account/security");
  }

}
