/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.AnalysisScheduler;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AnalysisSchedulerConfigurationTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void testDefaults() {
    var config = AnalysisSchedulerConfiguration.builder()
      .build();
    assertThat(config.getWorkDir()).isNull();
    assertThat(config.getEffectiveSettings()).isEmpty();
    assertThat(config.getClientPid()).isZero();
  }

  @Test
  void extraProps() {
    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("foo", "bar");
    var config = AnalysisSchedulerConfiguration.builder()
      .setExtraProperties(extraProperties)
      .build();
    assertThat(config.getEffectiveSettings()).containsOnly(entry("foo", "bar"));
  }

  @Test
  void effectiveConfig_should_add_nodejs() {
    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("foo", "bar");
    var config = AnalysisSchedulerConfiguration.builder()
      .setExtraProperties(extraProperties)
      .setNodeJs(Paths.get("nodejsPath"))
      .build();
    assertThat(config.getEffectiveSettings()).containsOnly(entry("foo", "bar"), entry("sonar.nodejs.executable", "nodejsPath"));
  }

  @Test
  void overrideDirs(@TempDir Path temp) throws Exception {
    var work = createDirectory(temp.resolve("work"));
    var config = AnalysisSchedulerConfiguration.builder()
      .setWorkDir(work)
      .build();
    assertThat(config.getWorkDir()).isEqualTo(work);
  }

  @Test
  void providePid() {
    var config = AnalysisSchedulerConfiguration.builder().setClientPid(123).build();
    assertThat(config.getClientPid()).isEqualTo(123);
  }

  @Test
  void should_not_fail_if_module_supplier_is_not_provided(@TempDir Path workDir) {
    assertDoesNotThrow(() -> {
      var analysisGlobalConfig = AnalysisSchedulerConfiguration.builder().setClientPid(1234L).setWorkDir(workDir).build();
      var result = new PluginsLoader().load(new PluginsLoader.Configuration(Set.of(), Set.of(), false, Optional.empty()), Set.of());
      new AnalysisScheduler(analysisGlobalConfig, result.getLoadedPlugins(), logTester.getLogOutput());
    });
  }
}
