/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2020 SonarSource SA
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

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.client.api.common.Language;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;

class StandaloneGlobalConfigurationTests {

  @Test
  void testDefaults() {
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .build();
    assertThat(config.getPluginUrls()).isEmpty();
    assertThat(config.getSonarLintUserHome()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint"));
    assertThat(config.getWorkDir()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "work"));
    assertThat(config.extraProperties()).isEmpty();
    assertThat(config.getEnabledLanguages()).isEmpty();
  }

  @Test
  void extraProps() throws Exception {
    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("foo", "bar");
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .setExtraProperties(extraProperties)
      .build();
    assertThat(config.extraProperties()).containsEntry("foo", "bar");
  }

  @Test
  void overrideDirs(@TempDir Path temp) throws Exception {
    Path sonarUserHome = createDirectory(temp.resolve("userHome"));
    Path work = createDirectory(temp.resolve("work"));
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .setSonarLintUserHome(sonarUserHome)
      .setWorkDir(work)
      .build();
    assertThat(config.getSonarLintUserHome()).isEqualTo(sonarUserHome);
    assertThat(config.getWorkDir()).isEqualTo(work);
  }

  @Test
  void configurePlugins() throws Exception {
    URL plugin1 = new URL("file://plugin1.jar");
    URL plugin2 = new URL("file://plugin2.jar");
    URL plugin3 = new URL("file://plugin3.jar");
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .addPlugin(plugin1)
      .addPlugins(plugin2, plugin3)
      .build();
    assertThat(config.getPluginUrls()).containsExactly(plugin1, plugin2, plugin3);
  }

  @Test
  void configureLanguages() throws Exception {
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .addEnabledLanguage(Language.JAVA)
      .addEnabledLanguages(Language.JS, Language.TS)
      .build();
    assertThat(config.getEnabledLanguages()).containsExactly(Language.JAVA, Language.JS, Language.TS);
  }
}
