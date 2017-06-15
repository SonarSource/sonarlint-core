/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.lang.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class StorageManagerTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void encodeModuleKeyForFs() throws Exception {

    Path sonarUserHome = temp.newFolder().toPath();
    StoragePaths manager = new StoragePaths(ConnectedGlobalConfiguration.builder()
      .setSonarLintUserHome(sonarUserHome)
      .setServerId("server_id")
      .build());

    Path moduleStorageRoot = manager.getModuleStorageRoot("module.:key/with_branch%");
    assertThat(moduleStorageRoot).isEqualTo(sonarUserHome.resolve("storage").resolve("server_id").resolve("modules").resolve("module.%3Akey%2Fwith_branch%25"));
  }

  @Test
  public void encodeServerIdForFs() throws Exception {

    Path sonarUserHome = temp.newFolder().toPath();
    StoragePaths manager = new StoragePaths(ConnectedGlobalConfiguration.builder()
      .setSonarLintUserHome(sonarUserHome)
      .setServerId("complicated.:name/with_invalid%chars")
      .build());

    Path storageRoot = manager.getServerStorageRoot();
    assertThat(storageRoot).isEqualTo(sonarUserHome.resolve("storage").resolve("complicated.%3Aname%2Fwith_invalid%25chars"));
  }

  @Test
  public void encodeTooLongServerId() throws Exception {

    Path sonarUserHome = temp.newFolder().toPath();
    StoragePaths manager = new StoragePaths(ConnectedGlobalConfiguration.builder()
      .setSonarLintUserHome(sonarUserHome)
      .setServerId(StringUtils.repeat("a", 260))
      .build());

    Path storageRoot = manager.getServerStorageRoot();
    String folderName = StringUtils.repeat("a", 223) + "6eeae9bf4dbb517d471f397af83bc76b";
    assertThat(folderName.length()).isLessThanOrEqualTo(255);
    assertThat(storageRoot).isEqualTo(sonarUserHome.resolve("storage").resolve(folderName));
  }

  @Test
  public void paths() throws IOException {
    Path sonarUserHome = temp.newFolder().toPath();
    StoragePaths manager = new StoragePaths(ConnectedGlobalConfiguration.builder()
      .setSonarLintUserHome(sonarUserHome)
      .setServerId("server")
      .build());

    assertThat(manager.getServerIssuesPath("module")).isEqualTo(sonarUserHome
      .resolve("storage")
      .resolve("server")
      .resolve("modules")
      .resolve("module")
      .resolve("server_issues"));

    assertThat(manager.getModuleListPath()).isEqualTo(sonarUserHome
      .resolve("storage")
      .resolve("server")
      .resolve("global")
      .resolve("module_list.pb"));
  }

  @Test
  public void readModuleList() {

  }

}
