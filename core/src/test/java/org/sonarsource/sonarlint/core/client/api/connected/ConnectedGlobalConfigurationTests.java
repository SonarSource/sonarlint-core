/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.Language;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.fail;

class ConnectedGlobalConfigurationTests {

  @Test
  void testDefaults() {
    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .build();
    assertThat(config.getConnectionId()).isNull();
    assertThat(config.getSonarLintUserHome()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint"));
    assertThat(config.getStorageRoot()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "storage"));
    assertThat(config.getWorkDir()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "work"));
    assertThat(config.extraProperties()).isEmpty();
    assertThat(config.getEmbeddedPluginPathsByKey()).isEmpty();
    assertThat(config.getClientPid()).isZero();
  }

  @Test
  void extraProps() {
    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("foo", "bar");
    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setExtraProperties(extraProperties)
      .build();
    assertThat(config.extraProperties()).containsEntry("foo", "bar");
  }

  @Test
  void overrideDirs(@TempDir Path temp) throws Exception {
    var sonarUserHome = createDirectory(temp.resolve("userHome"));
    var storage = createDirectory(temp.resolve("storage"));
    var work = createDirectory(temp.resolve("work"));
    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setSonarLintUserHome(sonarUserHome)
      .setStorageRoot(storage)
      .setWorkDir(work)
      .build();
    assertThat(config.getSonarLintUserHome()).isEqualTo(sonarUserHome);
    assertThat(config.getStorageRoot()).isEqualTo(storage);
    assertThat(config.getWorkDir()).isEqualTo(work);
  }

  @Test
  void enableLanguages() {
    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .addEnabledLanguages(Language.JAVA, Language.ABAP)
      .addEnabledLanguage(Language.C)
      .build();
    assertThat(config.getEnabledLanguages()).containsOnly(Language.JAVA, Language.ABAP, Language.C);
  }

  @Test
  void overridesPlugins() {
    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .useEmbeddedPlugin(Language.JAVA.getLanguageKey(), Paths.get("java.jar"))
      .useEmbeddedPlugin(Language.ABAP.getLanguageKey(), Paths.get("abap.jar"))
      .build();
    assertThat(config.getEmbeddedPluginPathsByKey()).containsOnly(entry("java", Paths.get("java.jar")),
      entry("abap", Paths.get("abap.jar")));
  }

  @Test
  void configureServerId() {
    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("myServer")
      .build();
    assertThat(config.getConnectionId()).isEqualTo("myServer");
  }

  @Test
  void validateServerId() {
    var builder = ConnectedGlobalConfiguration.sonarQubeBuilder();
    expectFailure(builder, "");
    expectFailure(builder, null);
  }

  private void expectFailure(ConnectedGlobalConfiguration.Builder builder, String serverId) {
    try {
      builder.setConnectionId(serverId);
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("'" + serverId + "' is not a valid connection ID");
    }
  }

  @Test
  void providePid() {
    var config = ConnectedGlobalConfiguration.sonarQubeBuilder().setClientPid(123).build();
    assertThat(config.getClientPid()).isEqualTo(123);
  }
}
