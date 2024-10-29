/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo.SeverityModeDetails.DEFAULT;
import static org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo.SeverityModeDetails.MQR;
import static org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo.SeverityModeDetails.STANDARD;

class ServerInfoSynchronizerTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @TempDir
  Path tmpDir;
  private ServerInfoSynchronizer synchronizer;

  @BeforeEach
  void prepare() {
    var storage = new ConnectionStorage(tmpDir, tmpDir, "connectionId");
    synchronizer = new ServerInfoSynchronizer(storage);
  }

  @Test
  void it_should_read_version_from_storage_when_available() throws IOException {
    var connectionPath = tmpDir.resolve("636f6e6e656374696f6e4964");
    Files.createDirectory(connectionPath);
    ProtobufFileUtil.writeToFile(Sonarlint.ServerInfo.newBuilder().setVersion("1.0.0").build(), connectionPath.resolve("server_info.pb"));

    var storedServerInfo = synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()), new SonarLintCancelMonitor());

    assertThat(storedServerInfo)
      .extracting(StoredServerInfo::getVersion)
      .hasToString("1.0.0");
  }

  @Test
  void it_should_synchronize_version_and_mode_when_not_supported() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}");

    var storedServerInfo = synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()), new SonarLintCancelMonitor());

    assertThat(storedServerInfo)
      .extracting(StoredServerInfo::getVersion, StoredServerInfo::getSeverityMode)
      .containsExactly(Version.create("9.9"), DEFAULT);
  }

  @Test
  void it_should_synchronize_version_and_mode_when_supported() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": \"UP\"}");
    mockServer.addProtobufResponse("/api/settings/values.protobuf?keys=sonar.multi-quality-mode.enabled", Settings.ValuesWsResponse.newBuilder()
      .addSettings(Settings.Setting.newBuilder()
        .setKey("sonar.multi-quality-mode.enabled")
        .setValue("true"))
      .build());

    var storedServerInfo = synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()), new SonarLintCancelMonitor());

    assertThat(storedServerInfo)
      .extracting(StoredServerInfo::getVersion, StoredServerInfo::getSeverityMode)
      .containsExactly(Version.create("10.8"), MQR);
  }

  @Test
  void it_should_synchronize_standard_mode() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": \"UP\"}");
    mockServer.addProtobufResponse("/api/settings/values.protobuf?keys=sonar.multi-quality-mode.enabled", Settings.ValuesWsResponse.newBuilder()
      .addSettings(Settings.Setting.newBuilder()
        .setKey("sonar.multi-quality-mode.enabled")
        .setValue("false"))
      .build());

    var storedServerInfo = synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()), new SonarLintCancelMonitor());

    assertThat(storedServerInfo)
      .extracting(StoredServerInfo::getVersion, StoredServerInfo::getSeverityMode)
      .containsExactly(Version.create("10.8"), STANDARD);
  }

  @Test
  void it_should_fail_when_server_is_down() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"DOWN\"}");

    var throwable = catchThrowable(() -> synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()), new SonarLintCancelMonitor()));

    assertThat(throwable)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Server not ready (DOWN)");
  }
}
