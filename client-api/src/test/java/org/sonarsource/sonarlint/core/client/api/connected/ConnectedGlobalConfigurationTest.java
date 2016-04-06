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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ConnectedGlobalConfigurationTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testDefaults() {
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .build();
    assertThat(config.getServerId()).isNull();
    assertThat(config.getSonarLintUserHome()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint"));
    assertThat(config.getStorageRoot()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "storage"));
    assertThat(config.getWorkDir()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "work"));
  }

  @Test
  public void overrideDirs() throws Exception {
    Path sonarUserHome = temp.newFolder().toPath();
    Path storage = temp.newFolder().toPath();
    Path work = temp.newFolder().toPath();
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setSonarLintUserHome(sonarUserHome)
      .setStorageRoot(storage)
      .setWorkDir(work)
      .build();
    assertThat(config.getSonarLintUserHome()).isEqualTo(sonarUserHome);
    assertThat(config.getStorageRoot()).isEqualTo(storage);
    assertThat(config.getWorkDir()).isEqualTo(work);
  }

  @Test
  public void configureServerId() throws Exception {
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setServerId("myServer")
      .build();
    assertThat(config.getServerId()).isEqualTo("myServer");
  }

  @Test
  public void validateServerId() throws Exception {
    ConnectedGlobalConfiguration.Builder builder = ConnectedGlobalConfiguration.builder();
    expectFailure(builder, "");
    expectFailure(builder, null);
  }

  private void expectFailure(ConnectedGlobalConfiguration.Builder builder, String serverId) {
    try {
      builder.setServerId(serverId);
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("'" + serverId + "' is not a valid server ID");
    }
  }
}
