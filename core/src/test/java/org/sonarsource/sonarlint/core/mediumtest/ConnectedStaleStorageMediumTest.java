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
package org.sonarsource.sonarlint.core.mediumtest;

import java.io.File;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalStorageUpdateRequiredException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpLogOutput;
import static org.sonarsource.sonarlint.core.mediumtest.fixtures.StorageFixture.newStorage;

public class ConnectedStaleStorageMediumTest {
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static File baseDir;
  private static ConnectedGlobalConfiguration config;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    String storageId = "localhost";
    newStorage(storageId)
      .stale()
      .create(slHome);

    config = ConnectedGlobalConfiguration.builder()
      .setConnectionId(storageId)
      .setSonarLintUserHome(slHome)
      .setLogOutput(createNoOpLogOutput())
      .build();
    baseDir = temp.newFolder();
  }

  @Test
  public void test_stale_global() {
    ConnectedSonarLintEngine sonarlint = new ConnectedSonarLintEngineImpl(config);

    assertThat(sonarlint.getState()).isEqualTo(State.NEED_UPDATE);
    assertThat(sonarlint.getGlobalStorageStatus()).isNotNull();
    assertThat(sonarlint.getProjectStorageStatus("foo")).isNull();

    try {
      sonarlint.allProjectsByKey();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(GlobalStorageUpdateRequiredException.class).hasMessage("Storage of server 'localhost' requires an update");
    }

    try {
      sonarlint.getRuleDetails("rule");
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(GlobalStorageUpdateRequiredException.class).hasMessage("Storage of server 'localhost' requires an update");
    }

    try {
      sonarlint.analyze(
        ConnectedAnalysisConfiguration.builder()
          .setBaseDir(baseDir.toPath())
          .build(),
        mock(IssueListener.class), null, null);
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(GlobalStorageUpdateRequiredException.class).hasMessage("Storage of server 'localhost' requires an update");
    }
  }
}
