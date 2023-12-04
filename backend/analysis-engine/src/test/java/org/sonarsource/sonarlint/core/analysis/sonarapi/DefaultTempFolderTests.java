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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultTempFolderTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void createTempFolderAndFile(@TempDir File rootTempFolder) throws Exception {
    var underTest = new DefaultTempFolder(rootTempFolder);
    var dir = underTest.newDir();
    assertThat(dir).exists().isDirectory();
    var file = underTest.newFile();
    assertThat(file).exists().isFile();

    underTest.clean();
    assertThat(rootTempFolder).doesNotExist();
  }

  @Test
  void createTempFolderWithName(@TempDir File rootTempFolder) throws Exception {
    var underTest = new DefaultTempFolder(rootTempFolder);
    var dir = underTest.newDir("sample");
    assertThat(dir).exists().isDirectory();
    assertThat(new File(rootTempFolder, "sample")).isEqualTo(dir);

    underTest.clean();
    assertThat(rootTempFolder).doesNotExist();
  }

  @Test
  void newDir_throws_ISE_if_name_is_not_valid(@TempDir File rootTempFolder) throws Exception {
    var underTest = new DefaultTempFolder(rootTempFolder);
    var tooLong = new StringBuilder("tooooolong");
    for (var i = 0; i < 50; i++) {
      tooLong.append("tooooolong");
    }

    var thrown = assertThrows(IllegalStateException.class, () -> underTest.newDir(tooLong.toString()));
    assertThat(thrown).hasMessageStartingWith("Failed to create temp directory");
  }

  @Test
  void newFile_throws_ISE_if_name_is_not_valid(@TempDir File rootTempFolder) throws Exception {
    var underTest = new DefaultTempFolder(rootTempFolder);
    var tooLong = new StringBuilder("tooooolong");
    for (var i = 0; i < 50; i++) {
      tooLong.append("tooooolong");
    }

    var thrown = assertThrows(IllegalStateException.class, () -> underTest.newFile(tooLong.toString(), ".txt"));
    assertThat(thrown).hasMessage("Failed to create temp file");
  }

  @Test
  void clean_deletes_non_empty_directory(@TempDir File dir) throws Exception {
    FileUtils.touch(new File(dir, "foo.txt"));

    var underTest = new DefaultTempFolder(dir);
    underTest.clean();

    assertThat(dir).doesNotExist();
  }

  @Test
  void clean_does_not_fail_if_directory_has_already_been_deleted(@TempDir File dir) throws Exception {
    var underTest = new DefaultTempFolder(dir);
    underTest.clean();
    assertThat(dir).doesNotExist();

    // second call does not fail, nor log ERROR logs
    underTest.clean();

    assertThat(logTester.logs(Level.ERROR)).isEmpty();
  }
}
