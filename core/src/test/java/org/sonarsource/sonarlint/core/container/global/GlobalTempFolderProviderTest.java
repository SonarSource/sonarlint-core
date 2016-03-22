/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.global;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalTempFolderProviderTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private GlobalTempFolderProvider tempFolderProvider = new GlobalTempFolderProvider();

  @Test
  public void createTempFolderProps() throws Exception {
    File workingDir = temp.newFolder();

    TempFolder tempFolder = tempFolderProvider.provide(StandaloneGlobalConfiguration.builder().setWorkDir(workingDir.toPath()).build());
    tempFolder.newDir();
    tempFolder.newFile();
    assertThat(getCreatedTempDir(workingDir)).exists();
    assertThat(getCreatedTempDir(workingDir).list()).hasSize(2);

    FileUtils.deleteQuietly(workingDir);
  }

  @Test
  public void cleanUpOld() throws IOException {
    long creationTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(100);
    File workingDir = temp.newFolder();

    for (int i = 0; i < 3; i++) {
      File tmp = new File(workingDir, ".sonartmp_" + i);
      tmp.mkdirs();
      setFileCreationDate(tmp, creationTime);
    }

    tempFolderProvider.provide(StandaloneGlobalConfiguration.builder().setWorkDir(workingDir.toPath()).build());
    // this also checks that all other temps were deleted
    assertThat(getCreatedTempDir(workingDir)).exists();

    FileUtils.deleteQuietly(workingDir);
  }

  @Test
  public void createTempFolderSonarHome() throws Exception {
    // with sonar home, it will be in {sonar.home}/.sonartmp
    File sonarHome = temp.newFolder();
    File workingDir = new File(sonarHome, StandaloneGlobalConfiguration.DEFAULT_WORK_DIR).getAbsoluteFile();

    TempFolder tempFolder = tempFolderProvider.provide(StandaloneGlobalConfiguration.builder().setSonarLintUserHome(sonarHome.toPath()).build());
    tempFolder.newDir();
    tempFolder.newFile();
    assertThat(getCreatedTempDir(workingDir)).exists();
    assertThat(getCreatedTempDir(workingDir).list()).hasSize(2);

    FileUtils.deleteQuietly(sonarHome);
  }

  private File getCreatedTempDir(File workingDir) {
    assertThat(workingDir).isDirectory();
    assertThat(workingDir.listFiles()).hasSize(1);
    return workingDir.listFiles()[0];
  }

  private void setFileCreationDate(File f, long time) throws IOException {
    BasicFileAttributeView attributes = Files.getFileAttributeView(f.toPath(), BasicFileAttributeView.class);
    FileTime creationTime = FileTime.fromMillis(time);
    attributes.setTimes(creationTime, creationTime, creationTime);
  }
}
