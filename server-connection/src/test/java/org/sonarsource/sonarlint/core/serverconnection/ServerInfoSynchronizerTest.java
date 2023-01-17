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

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ServerInfoSynchronizerTest {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @TempDir
  Path tmpDir;
  private ServerInfoSynchronizer synchronizer;

  @BeforeEach
  void prepare() {
    synchronizer = new ServerInfoSynchronizer(new ServerInfoStorage(tmpDir));
  }

  @Test
  void it_should_read_version_from_storage_when_available() {
    ProtobufUtil.writeToFile(Sonarlint.ServerInfo.newBuilder().setVersion("1.0.0").build(), tmpDir.resolve("server_info.pb"));

    var storedServerInfo = synchronizer.readOrSynchronizeServerInfo(null);

    assertThat(storedServerInfo)
      .extracting("version")
      .hasToString("1.0.0");
  }

  @Test
  void it_should_synchronize_version_when_not_available() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");

    var storedServerInfo = synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), MockWebServerExtension.httpClient()));

    assertThat(storedServerInfo)
      .extracting("version")
      .hasToString("7.9");
  }

  @Test
  void it_should_fail_when_server_is_down() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"DOWN\"}");

    var throwable = catchThrowable(() -> synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), MockWebServerExtension.httpClient())));

    assertThat(throwable)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Server not ready (DOWN)");
  }
}
