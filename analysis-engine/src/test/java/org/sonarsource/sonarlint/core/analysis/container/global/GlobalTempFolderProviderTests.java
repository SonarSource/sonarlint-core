/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalTempFolderProviderTests {

  @TempDir
  private Path workingDir;

  private final GlobalTempFolderProvider tempFolderProvider = new GlobalTempFolderProvider();

  @Test
  void createTempFolderProps() throws Exception {

    TempFolder tempFolder = tempFolderProvider.provide(AnalysisEngineConfiguration.builder().setWorkDir(workingDir).build());
    tempFolder.newDir();
    tempFolder.newFile();
    assertThat(getCreatedTempDir(workingDir)).exists();
    assertThat(getCreatedTempDir(workingDir).list()).hasSize(2);

    FileUtils.deleteQuietly(workingDir.toFile());
  }

  @Test
  void cleanUpOld() throws IOException {
    var creationTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(100);

    for (var i = 0; i < 3; i++) {
      var tmp = new File(workingDir.toFile(), ".sonarlinttmp_" + i);
      tmp.mkdirs();
      setFileCreationDate(tmp, creationTime);
    }

    tempFolderProvider.provide(AnalysisEngineConfiguration.builder().setWorkDir(workingDir).build());
    // this also checks that all other temps were deleted
    assertThat(getCreatedTempDir(workingDir)).exists();

    FileUtils.deleteQuietly(workingDir.toFile());
  }

  private File getCreatedTempDir(Path workingDir) {
    assertThat(workingDir).isDirectory();
    assertThat(workingDir.toFile().listFiles()).hasSize(1);
    return workingDir.toFile().listFiles()[0];
  }

  private void setFileCreationDate(File f, long time) throws IOException {
    var attributes = Files.getFileAttributeView(f.toPath(), BasicFileAttributeView.class);
    var creationTime = FileTime.fromMillis(time);
    attributes.setTimes(creationTime, creationTime, creationTime);
  }
}
