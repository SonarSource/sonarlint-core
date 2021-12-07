/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Settings.Setting;
import org.sonarqube.ws.Settings.Values;
import org.sonarqube.ws.Settings.ValuesWsResponse;
import org.sonarsource.sonarlint.core.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.container.storage.GlobalSettingsStore;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsDownloaderTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @Test
  void testFetchGlobalSettings(@TempDir Path tempDir) {
    GlobalSettingsStore globalSettingsStore = new GlobalSettingsStore(new StorageFolder.Default(tempDir));
    SettingsDownloader underTest = new SettingsDownloader(mockServer.serverApiHelper(), globalSettingsStore);
    ValuesWsResponse response = ValuesWsResponse.newBuilder()
      .addSettings(Setting.newBuilder()
        .setKey("sonar.core.treemap.colormetric")
        .setValue("violations_density")
        .setInherited(true))
      .addSettings(Setting.newBuilder()
        .setKey("sonar.core.treemap.sizemetric")
        .setValue("ncloc"))
      .addSettings(Setting.newBuilder()
        .setKey("views.servers")
        .setValues(Values.newBuilder().addValues("135817900907501")))
      .build();
    mockServer.addProtobufResponse("/api/settings/values.protobuf", response);

    underTest.fetchGlobalSettings();

    GlobalProperties properties = ProtobufUtil.readFile(tempDir.resolve(GlobalSettingsStore.PROPERTIES_PB), GlobalProperties.parser());
    assertThat(properties.getPropertiesMap()).containsOnly(
      entry("sonar.core.treemap.sizemetric", "ncloc"),
      entry("views.servers", "135817900907501"));
  }

  @Test
  void invalidResponseSettings(@TempDir Path tempDir) {
    GlobalSettingsStore globalSettingsStore = new GlobalSettingsStore(new StorageFolder.Default(tempDir));
    SettingsDownloader underTest = new SettingsDownloader(mockServer.serverApiHelper(), globalSettingsStore);
    mockServer.addStringResponse("/api/settings/values.protobuf", "foo bar");

    assertThrows(IllegalStateException.class, underTest::fetchGlobalSettings);
  }

}
