/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.plugin.common.Language;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class GlobalAnalysisConfigurationTests {

  @Test
  void testDefaults() {
    GlobalAnalysisConfiguration config = GlobalAnalysisConfiguration.builder()
      .build();
    assertThat(config.getPluginsJarPaths()).isEmpty();
    assertThat(config.getSonarLintUserHome()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint"));
    assertThat(config.getWorkDir()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "work"));
    assertThat(config.extraProperties()).isEmpty();
    assertThat(config.getEnabledLanguages()).isEmpty();
    assertThat(config.getClientPid()).isZero();
  }

  @Test
  void extraProps() throws Exception {
    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("foo", "bar");
    GlobalAnalysisConfiguration config = GlobalAnalysisConfiguration.builder()
      .setExtraProperties(extraProperties)
      .build();
    assertThat(config.extraProperties()).containsOnly(entry("foo", "bar"));
  }

  @Test
  void effectiveConfig_should_add_nodejs() throws Exception {
    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("foo", "bar");
    GlobalAnalysisConfiguration config = GlobalAnalysisConfiguration.builder()
      .setExtraProperties(extraProperties)
      .setNodeJs(Paths.get("nodejsPath"), null)
      .build();
    assertThat(config.getEffectiveConfig()).containsOnly(entry("foo", "bar"), entry("sonar.nodejs.executable", "nodejsPath"));
  }

  @Test
  void overrideDirs(@TempDir Path temp) throws Exception {
    Path sonarUserHome = createDirectory(temp.resolve("userHome"));
    Path work = createDirectory(temp.resolve("work"));
    GlobalAnalysisConfiguration config = GlobalAnalysisConfiguration.builder()
      .setSonarLintUserHome(sonarUserHome)
      .setWorkDir(work)
      .build();
    assertThat(config.getSonarLintUserHome()).isEqualTo(sonarUserHome);
    assertThat(config.getWorkDir()).isEqualTo(work);
  }

  @Test
  void configurePlugins() throws Exception {
    Path plugin1 = Paths.get("plugin1.jar");
    Path plugin2 = Paths.get("plugin2.jar");
    Path plugin3 = Paths.get("plugin3.jar");
    GlobalAnalysisConfiguration config = GlobalAnalysisConfiguration.builder()
      .addPlugin(plugin1)
      .addPlugins(plugin2, plugin3)
      .build();
    assertThat(config.getPluginsJarPaths()).containsExactlyInAnyOrder(plugin1, plugin2, plugin3);
  }

  @Test
  void configureLanguages() throws Exception {
    GlobalAnalysisConfiguration config = GlobalAnalysisConfiguration.builder()
      .addEnabledLanguage(Language.JAVA)
      .addEnabledLanguages(Language.JS, Language.TS)
      .build();
    assertThat(config.getEnabledLanguages()).containsExactly(Language.JAVA, Language.JS, Language.TS);
  }

  @Test
  void providePid() {
    GlobalAnalysisConfiguration config = GlobalAnalysisConfiguration.builder().setClientPid(123).build();
    assertThat(config.getClientPid()).isEqualTo(123);
  }
}
