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
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Status;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.scan.filesystem.FileQuery;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;

import java.io.File;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

public class AdapterModuleFileSystemTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void should_search_input_files() throws Exception {
    File baseDir = temp.newFile();
    InputFile mainInput = new DefaultInputFile("foo", "Main.cbl").setModuleBaseDir(baseDir.toPath()).setStatus(Status.ADDED).setType(InputFile.Type.MAIN).setLanguage("cobol");
    InputFile testInput = new DefaultInputFile("foo", "Test.java").setModuleBaseDir(baseDir.toPath()).setStatus(Status.ADDED).setType(InputFile.Type.TEST);
    InputPathCache pathCache = new InputPathCache();
    pathCache.doAdd(mainInput);
    pathCache.doAdd(testInput);

    StandaloneAnalysisConfiguration config = new StandaloneAnalysisConfiguration(temp.getRoot().toPath(), temp.getRoot().toPath(), null, Collections.<String, String>emptyMap());
    SonarLintFileSystem sonarLintFileSystem = new SonarLintFileSystem(config, pathCache, null);

    AdapterModuleFileSystem fs = new AdapterModuleFileSystem(sonarLintFileSystem);

    Iterable<File> files = fs.files(FileQuery.onMain());
    assertThat(files).containsOnly(new File(baseDir, "Main.cbl"));

    files = fs.files(FileQuery.onMain().onLanguage("javascript"));
    assertThat(files).isEmpty();

    files = fs.files(FileQuery.onMain().onLanguage("cobol"));
    assertThat(files).containsOnly(new File(baseDir, "Main.cbl"));

    files = fs.files(FileQuery.onMain().on("STATUS", "ADDED"));
    assertThat(files).containsOnly(new File(baseDir, "Main.cbl"));

    files = fs.files(FileQuery.onMain().on("STATUS", "SAME"));
    assertThat(files).isEmpty();

    try {
      fs.files(FileQuery.onMain().on("INVALID", "VALUE"));
      fail("Expected exception");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void unsupported() {
    SonarLintFileSystem sonarLintFileSystem = mock(SonarLintFileSystem.class);
    AdapterModuleFileSystem fs = new AdapterModuleFileSystem(sonarLintFileSystem);
    try {
      fs.baseDir();
      fail("expected exception");
    } catch (UnsupportedOperationException e) {
    }

    try {
      fs.buildDir();
      fail("expected exception");
    } catch (UnsupportedOperationException e) {
    }

    try {
      fs.sourceDirs();
      fail("expected exception");
    } catch (UnsupportedOperationException e) {
    }

    try {
      fs.testDirs();
      fail("expected exception");
    } catch (UnsupportedOperationException e) {
    }

    try {
      fs.binaryDirs();
      fail("expected exception");
    } catch (UnsupportedOperationException e) {
    }

    try {
      fs.workingDir();
      fail("expected exception");
    } catch (UnsupportedOperationException e) {
    }

    try {
      fs.sourceCharset();
      fail("expected exception");
    } catch (UnsupportedOperationException e) {
    }
  }
}
