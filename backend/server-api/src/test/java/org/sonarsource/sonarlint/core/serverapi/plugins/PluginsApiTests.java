/*
 * SonarLint Core - Server API
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverapi.plugins;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PluginsApiTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @Test
  void should_return_installed_plugins() {
    var underTest = new PluginsApi(mockServer.serverApiHelper());
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"pluginKey\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"plugin-1.0.0.1234.jar\", \"sonarLintSupported\": true}" +
      "]}");

    var serverPlugins = underTest.getInstalled(new SonarLintCancelMonitor());

    assertThat(serverPlugins)
      .extracting("key", "hash", "filename", "sonarLintSupported")
      .containsOnly(tuple("pluginKey", "de5308f43260d357acc97712ce4c5475", "plugin-1.0.0.1234.jar", true));
    assertThat(serverPlugins.getFirst().getStaticResourceFolder()).isNull();
    assertThat(serverPlugins.getFirst().getStaticResources()).isEmpty();
  }

  @Test
  void should_return_static_resource_inventory() {
    var underTest = new PluginsApi(mockServer.serverApiHelper());
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [{" +
      "\"key\": \"javascript\", \"hash\": \"abc\", \"filename\": \"javascript.jar\", \"sonarLintSupported\": true," +
      "\"staticResourceFolder\": \"folder-hash\"," +
      "\"staticResources\": [{\"path\": \"linux-x64/native-tool\", \"hash\": \"deadbeef\", \"size\": 42}]" +
      "}]}");

    var serverPlugins = underTest.getInstalled(new SonarLintCancelMonitor());

    assertThat(serverPlugins).hasSize(1);
    assertThat(serverPlugins.getFirst().getStaticResourceFolder()).isEqualTo("folder-hash");
    assertThat(serverPlugins.getFirst().getStaticResources())
      .containsExactly(new StaticResource("linux-x64/native-tool", "deadbeef", 42));
  }

  @Test
  void should_download_a_static_resource_with_the_connection_credentials() {
    var underTest = new PluginsApi(mockServer.serverApiHelper());
    mockServer.addStringResponse("/static/javascript/linux-x64/native-tool", "bytes");

    underTest.getStaticResource("javascript", "linux-x64/native-tool", stream -> assertThat(stream).hasContent("bytes"), new SonarLintCancelMonitor());
  }

  @Test
  void should_return_sonarqube_server_plugin_content() {
    var underTest = new PluginsApi(mockServer.serverApiHelper());
    mockServer.addStringResponse("/api/plugins/download?plugin=pluginKey", "content");

    underTest.getPlugin("pluginKey", stream -> assertThat(stream).hasContent("content"), new SonarLintCancelMonitor());
  }

}
