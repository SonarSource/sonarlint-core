/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverapi.plugins.StaticResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PluginsStorageTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path storageRoot;
  PluginsStorage underTest;

  @BeforeEach
  void setUp() {
    underTest = new PluginsStorage(storageRoot);
  }

  @Test
  void should_consider_storage_invalid_if_file_doesnt_exist() {
    assertFalse(underTest.isValid());
  }

  @Test
  void stores_the_static_resource_inventory_with_the_plugin_reference() {
    var resource = new StaticResource("linux-x64/native-tool", "deadbeef", 42);
    var plugin = new ServerPlugin("javascript", "jar-hash", "javascript.jar", true, "folder-hash", List.of(resource));

    underTest.store(plugin, new ByteArrayInputStream("jar".getBytes(StandardCharsets.UTF_8)));

    var stored = underTest.getStoredPluginsByKey().get("javascript");
    assertThat(stored.getStaticResourceFolder()).isEqualTo("folder-hash");
    assertThat(stored.getStaticResources()).containsExactly(resource);
    assertThat(stored.getJarPath()).isEqualTo(storageRoot.resolve("plugins").resolve("javascript.jar"));
  }

  @Test
  void cleanup_keeps_the_static_resource_inventory() {
    var resource = new StaticResource("linux-x64/native-tool", "deadbeef", 42);
    var plugin = new ServerPlugin("javascript", "jar-hash", "javascript.jar", true, "folder-hash", List.of(resource));
    underTest.store(plugin, new ByteArrayInputStream("jar".getBytes(StandardCharsets.UTF_8)));

    underTest.cleanUpUnknownPlugins(List.of(plugin));

    assertThat(underTest.getStoredPluginsByKey().get("javascript").getStaticResources()).containsExactly(resource);
  }

  @Test
  void cleanup_leaves_cached_resources_beside_the_plugins_directory() throws IOException {
    var cached = storageRoot.resolve("resources").resolve("abc").resolve("native-tool");
    Files.createDirectories(cached.getParent());
    Files.writeString(cached, "cached");

    underTest.cleanUpUnknownPlugins(List.of());

    assertThat(cached).hasContent("cached");
  }

}
