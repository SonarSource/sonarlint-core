/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.plugins;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PluginsApiTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @Test
  void should_return_installed_plugins() {
    var underTest = new PluginsApi(mockServer.serverApiHelper());
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"pluginKey\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"plugin-1.0.0.1234.jar\", \"sonarLintSupported\": true}" +
      "]}");

    var serverPlugins = underTest.getInstalled();

    assertThat(serverPlugins)
      .extracting("key", "hash", "filename", "sonarLintSupported")
      .containsOnly(tuple("pluginKey", "de5308f43260d357acc97712ce4c5475", "plugin-1.0.0.1234.jar", true));
  }

  @Test
  void should_return_plugin_content() {
    var underTest = new PluginsApi(mockServer.serverApiHelper());
    mockServer.addStringResponse("/api/plugins/download?plugin=pluginKey", "content");

    underTest.getPlugin("pluginKey", stream -> assertThat(stream).hasContent("content"));
  }

}
