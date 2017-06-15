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
package org.sonarsource.sonarlint.core.mediumtest;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalUpdateRequiredException;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpLogOutput;

public class ConnectedStaleStorageMediumTest {
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static File baseDir;
  private static ConnectedGlobalConfiguration config;
  private static Path storage;

  @BeforeClass
  public static void prepare() throws Exception {
    String storageId = "localhost";
    Path slHome = temp.newFolder().toPath();
    storage = slHome.resolve("storage").resolve(storageId);

    config = ConnectedGlobalConfiguration.builder()
      .setServerId(storageId)
      .setSonarLintUserHome(slHome)
      .setLogOutput(createNoOpLogOutput())
      .build();
    baseDir = temp.newFolder();
  }

  private static void writeUpdateStatus(Path storage, String version) throws IOException {
    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(version)
      .setClientUserAgent("agent")
      .setSonarlintCoreVersion("1.0")
      .setUpdateTimestamp(new Date().getTime())
      .build();
    Path global = storage.resolve("global");
    Files.createDirectories(global);
    ProtobufUtil.writeToFile(storageStatus, global.resolve(StoragePaths.STORAGE_STATUS_PB));
  }

  @Test
  public void test_stale_global() throws Exception {
    writeUpdateStatus(storage, "0");
    ConnectedSonarLintEngine sonarlint = new ConnectedSonarLintEngineImpl(config);

    assertThat(sonarlint.getState()).isEqualTo(State.NEED_UPDATE);
    assertThat(sonarlint.getGlobalStorageStatus()).isNotNull();
    assertThat(sonarlint.getModuleStorageStatus("foo")).isNull();

    try {
      sonarlint.allModulesByKey();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(GlobalUpdateRequiredException.class).hasMessage("Please update server 'localhost'");
    }

    try {
      sonarlint.getRuleDetails("rule");
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(GlobalUpdateRequiredException.class).hasMessage("Please update server 'localhost'");
    }

    try {
      sonarlint.analyze(
        new ConnectedAnalysisConfiguration(null, baseDir.toPath(), temp.newFolder().toPath(), Collections.<ClientInputFile>emptyList(), ImmutableMap.<String, String>of()),
        mock(IssueListener.class));
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(GlobalUpdateRequiredException.class).hasMessage("Please update server 'localhost'");
    }
  }
}
