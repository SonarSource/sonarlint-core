/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.client.api.common.Language;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class ConnectedGlobalConfigurationTests {

  @Test
  void testDefaults() {
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .build();
    assertThat(config.getServerId()).isNull();
    assertThat(config.getSonarLintUserHome()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint"));
    assertThat(config.getStorageRoot()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "storage"));
    assertThat(config.getWorkDir()).isEqualTo(Paths.get(System.getProperty("user.home"), ".sonarlint", "work"));
    assertThat(config.extraProperties()).isEmpty();
  }

  @Test
  void extraProps() throws Exception {
    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("foo", "bar");
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setExtraProperties(extraProperties)
      .build();
    assertThat(config.extraProperties()).containsEntry("foo", "bar");
  }

  @Test
  void overrideDirs(@TempDir Path temp) throws Exception {
    Path sonarUserHome = createDirectory(temp.resolve("userHome"));
    Path storage = createDirectory(temp.resolve("storage"));
    Path work = createDirectory(temp.resolve("work"));
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
  void enableLanguages() {
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .addEnabledLanguages(Language.JAVA, Language.ABAP)
      .addEnabledLanguage(Language.C)
      .build();
    assertThat(config.getEnabledLanguages()).containsOnly(Language.JAVA, Language.ABAP, Language.C);
  }

  @Test
  void configureServerId() throws Exception {
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setServerId("myServer")
      .build();
    assertThat(config.getServerId()).isEqualTo("myServer");
  }

  @Test
  void validateServerId() throws Exception {
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
