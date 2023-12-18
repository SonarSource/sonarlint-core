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
package org.sonarsource.sonarlint.core.client.api.standalone;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.Language;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;

class StandaloneGlobalConfigurationTests {

  @Test
  void testDefaults() {
    var config = StandaloneGlobalConfiguration.builder()
      .build();
    assertThat(config.getPluginPaths()).isEmpty();
    assertThat(config.getSonarLintUserHome()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint"));
    assertThat(config.getWorkDir()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "work"));
    assertThat(config.extraProperties()).isEmpty();
    assertThat(config.getEnabledLanguages()).isEmpty();
    assertThat(config.getClientPid()).isZero();
  }

  @Test
  void extraProps() {
    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("foo", "bar");
    var config = StandaloneGlobalConfiguration.builder()
      .setExtraProperties(extraProperties)
      .build();
    assertThat(config.extraProperties()).containsEntry("foo", "bar");
  }

  @Test
  void overrideDirs(@TempDir Path temp) throws Exception {
    var sonarUserHome = createDirectory(temp.resolve("userHome"));
    var work = createDirectory(temp.resolve("work"));
    var config = StandaloneGlobalConfiguration.builder()
      .setSonarLintUserHome(sonarUserHome)
      .setWorkDir(work)
      .build();
    assertThat(config.getSonarLintUserHome()).isEqualTo(sonarUserHome);
    assertThat(config.getWorkDir()).isEqualTo(work);
  }

  @Test
  void configurePlugins() {
    var plugin1 = Paths.get("plugin1.jar");
    var plugin2 = Paths.get("plugin2.jar");
    var plugin3 = Paths.get("plugin3.jar");
    var config = StandaloneGlobalConfiguration.builder()
      .addPlugin(plugin1)
      .addPlugins(plugin2, plugin3)
      .build();
    assertThat(config.getPluginPaths()).containsExactlyInAnyOrder(plugin1, plugin2, plugin3);
  }

  @Test
  void configureLanguages() {
    var config = StandaloneGlobalConfiguration.builder()
      .addEnabledLanguage(Language.JAVA)
      .addEnabledLanguages(Language.JS, Language.TS)
      .build();
    assertThat(config.getEnabledLanguages()).containsExactly(Language.JAVA, Language.JS, Language.TS);
  }

  @Test
  void providePid() {
    var config = StandaloneGlobalConfiguration.builder().setClientPid(123).build();
    assertThat(config.getClientPid()).isEqualTo(123);
  }
}
