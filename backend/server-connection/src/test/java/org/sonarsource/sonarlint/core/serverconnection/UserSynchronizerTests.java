/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

class UserSynchronizerTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @TempDir
  Path tmpDir;
  private UserSynchronizer synchronizer;
  private ConnectionStorage storage;

  @BeforeEach
  void prepare() {
    storage = new ConnectionStorage(tmpDir, tmpDir, "connectionId");
    synchronizer = new UserSynchronizer(storage);
  }

  @Test
  void it_should_synchronize_user_id_on_sonarcloud() {
    mockServer.addStringResponse("/api/users/current", """
      {
        "isLoggedIn": true,
        "id": "16c9b3b3-3f7e-4d61-91fe-31d731456c08",
        "login": "obiwan.kenobi"
      }""");

    var serverApi = new ServerApi(mockServer.endpointParams("orgKey"), HttpClientProvider.forTesting().getHttpClient());
    synchronizer.synchronize(serverApi, new SonarLintCancelMonitor());

    var storedUserId = storage.user().read();
    assertThat(storedUserId)
      .isPresent()
      .contains("16c9b3b3-3f7e-4d61-91fe-31d731456c08");
  }

  @Test
  void it_should_not_synchronize_on_sonarqube_server() {
    var serverApi = new ServerApi(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient());
    synchronizer.synchronize(serverApi, new SonarLintCancelMonitor());

    var storedUserId = storage.user().read();
    assertThat(storedUserId).isEmpty();
  }

  @Test
  void it_should_not_store_null_user_id() {
    mockServer.addStringResponse("/api/users/current", "{}");

    var serverApi = new ServerApi(mockServer.endpointParams("orgKey"), HttpClientProvider.forTesting().getHttpClient());
    synchronizer.synchronize(serverApi, new SonarLintCancelMonitor());

    var storedUserId = storage.user().read();
    assertThat(storedUserId).isEmpty();
  }

  @Test
  void it_should_store_user_id_in_correct_file() throws IOException {
    mockServer.addStringResponse("/api/users/current", """
      {
        "isLoggedIn": true,
        "id": "test-user-id",
        "login": "test.user"
      }""");

    var serverApi = new ServerApi(mockServer.endpointParams("orgKey"), HttpClientProvider.forTesting().getHttpClient());
    synchronizer.synchronize(serverApi, new SonarLintCancelMonitor());

    var connectionPath = tmpDir.resolve("636f6e6e656374696f6e4964");
    var userFile = connectionPath.resolve("user.pb");
    assertThat(userFile).exists();
    assertThat(Files.size(userFile)).isGreaterThan(0);
  }

}

