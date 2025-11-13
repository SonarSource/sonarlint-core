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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.monitoring.DogfoodEnvironmentDetectionService;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

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
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);
    var storage = new ConnectionStorage(tmpDir, tmpDir, "connectionId", dogfoodEnvDetectionService, databaseService);
    synchronizer = new ServerInfoSynchronizer(storage);
  }

  @Test
  void it_should_read_version_from_storage_when_available() throws IOException {
    var connectionPath = tmpDir.resolve("636f6e6e656374696f6e4964");
    Files.createDirectory(connectionPath);
    ProtobufFileUtil.writeToFile(Sonarlint.ServerInfo.newBuilder().setVersion("1.0.0").build(), connectionPath.resolve("server_info.pb"));

    var storedServerInfo = synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()),
      new SonarLintCancelMonitor());

    assertThat(storedServerInfo)
      .extracting(StoredServerInfo::version)
      .hasToString("1.0.0");
  }

  @Test
  void it_should_synchronize_version_and_settings() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}");
    mockServer.addStringResponse("/api/features/list", "[\"sca\"]");
    mockServer.addProtobufResponse("/api/settings/values.protobuf", Settings.ValuesWsResponse.newBuilder()
      .addSettings(Settings.Setting.newBuilder()
        .setKey("sonar.multi-quality-mode.enabled")
        .setValue("true"))
      .addSettings(Settings.Setting.newBuilder()
        .setKey("sonar.earlyAccess.misra.enabled")
        .setValue("true"))
      .build());

    var storedServerInfo = synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()),
      new SonarLintCancelMonitor());

    assertThat(storedServerInfo)
      .extracting(StoredServerInfo::version, StoredServerInfo::features, StoredServerInfo::globalSettings)
      .containsExactly(Version.create("9.9"), Set.of(Feature.SCA),
        new ServerSettings(Map.of(
          "sonar.multi-quality-mode.enabled", "true",
          "sonar.earlyAccess.misra.enabled", "true",
          "sonar.misracompliance.enabled", "true")));
  }

  @Test
  void it_should_fail_when_server_is_down() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"DOWN\"}");

    var throwable = catchThrowable(
      () -> synchronizer.readOrSynchronizeServerInfo(new ServerApi(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()), new SonarLintCancelMonitor()));

    assertThat(throwable)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Server not ready (DOWN)");
  }
}
