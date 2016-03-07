/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.client.api;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

public class GlobalConfigurationTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testDefaults() {
    GlobalConfiguration config = GlobalConfiguration.builder()
      .build();
    assertThat(config.getPluginUrls()).isEmpty();
    assertThat(config.getServerId()).isNull();
    assertThat(config.getLogOutput()).isNull();
    assertThat(config.getSonarLintUserHome()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint"));
    assertThat(config.getStorageRoot()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "storage"));
    assertThat(config.getWorkDir()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "work"));
    assertThat(config.isVerbose()).isFalse();
  }

  @Test
  public void overrideDirs() throws Exception {
    Path sonarUserHome = temp.newFolder().toPath();
    Path storage = temp.newFolder().toPath();
    Path work = temp.newFolder().toPath();
    GlobalConfiguration config = GlobalConfiguration.builder()
      .setSonarLintUserHome(sonarUserHome)
      .setStorageRoot(storage)
      .setWorkDir(work)
      .build();
    assertThat(config.getSonarLintUserHome()).isEqualTo(sonarUserHome);
    assertThat(config.getStorageRoot()).isEqualTo(storage);
    assertThat(config.getWorkDir()).isEqualTo(work);
  }

  @Test
  public void configureLogs() {
    LogOutput logOutput = mock(LogOutput.class);
    GlobalConfiguration config = GlobalConfiguration.builder()
      .setVerbose(true)
      .setLogOutput(logOutput)
      .build();
    assertThat(config.getLogOutput()).isEqualTo(logOutput);
    assertThat(config.isVerbose()).isTrue();
  }

  @Test
  public void configurePlugins() throws Exception {
    URL plugin1 = new URL("file://plugin1.jar");
    URL plugin2 = new URL("file://plugin2.jar");
    URL plugin3 = new URL("file://plugin3.jar");
    GlobalConfiguration config = GlobalConfiguration.builder()
      .addPlugin(plugin1)
      .addPlugins(plugin2, plugin3)
      .build();
    assertThat(config.getPluginUrls()).containsExactly(plugin1, plugin2, plugin3);
  }

  @Test
  public void configureServerId() throws Exception {
    GlobalConfiguration config = GlobalConfiguration.builder()
      .setServerId("myServer")
      .build();
    assertThat(config.getServerId()).isEqualTo("myServer");
  }

  @Test
  public void server_id_and_plugins_are_exclusive() throws Exception {
    URL plugin1 = new URL("file://plugin1.jar");
    try {
      GlobalConfiguration.builder()
        .addPlugin(plugin1)
        .setServerId("myServer");
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Manual plugins already configured");
    }

    try {
      GlobalConfiguration.builder()
        .setServerId("myServer")
        .addPlugin(plugin1);
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Server id already configured");
    }
  }
}
