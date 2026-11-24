/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.scanner.HostPlatform;
import org.sonar.api.scanner.ResourceFetcher;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultResourceFetcher;
import org.sonarsource.sonarlint.core.analysis.sonarapi.SonarLintHostPlatform;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInstancesLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GlobalAnalysisContainerTests {

  private GlobalAnalysisContainer container;

  @AfterEach
  void stop() {
    if (container != null) {
      container.stopComponents();
    }
  }

  @Test
  void exposes_host_platform_and_resource_fetcher_to_child_containers(@TempDir Path workDir) {
    container = new GlobalAnalysisContainer(
      AnalysisSchedulerConfiguration.builder().setWorkDir(workDir).setClientPid(1L).build(),
      new LoadedPlugins(new HashMap<>(), mock(PluginInstancesLoader.class), Set.of(), Set.of()));
    container.startComponents();

    assertThat(container.getComponentByType(HostPlatform.class)).isInstanceOf(SonarLintHostPlatform.class);
    assertThat(container.getComponentByType(ResourceFetcher.class)).isInstanceOf(DefaultResourceFetcher.class);

    var analysisContainer = container.createChild();
    analysisContainer.startComponents();

    var hostPlatform = analysisContainer.getComponentByType(HostPlatform.class);
    assertThat(hostPlatform.getOs()).isNotNull();
    assertThat(hostPlatform.getArch()).isNotNull();
    var fetcher = analysisContainer.getComponentByType(ResourceFetcher.class);
    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> fetcher.fetchAndExtract("javascript", "linux-x64/native-tool.tar.gz"))
      .isInstanceOf(UnsupportedOperationException.class);
  }

}
