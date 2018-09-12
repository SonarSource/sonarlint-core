/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import java.nio.file.Path;
import java.util.Collections;
import org.junit.AfterClass;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

public class ConnectedEmptyStorageMediumTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static ConnectedSonarLintEngine sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setServerId("localhost")
      .setSonarLintUserHome(slHome)
      .setLogOutput((msg, level) -> {
      })
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop(true);
  }

  @Test
  public void test_no_storage() throws Exception {

    assertThat(sonarlint.getState()).isEqualTo(State.NEVER_UPDATED);
    assertThat(sonarlint.getGlobalStorageStatus()).isNull();
    assertThat(sonarlint.getProjectStorageStatus("foo")).isNull();

    try {
      sonarlint.allProjectsByKey();
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
        mock(IssueListener.class), null, null);
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(GlobalUpdateRequiredException.class).hasMessage("Please update server 'localhost'");
    }

  }

}
