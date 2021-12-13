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

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.mediumtest.fixtures.ProjectStorageFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpLogOutput;
import static org.sonarsource.sonarlint.core.mediumtest.fixtures.StorageFixture.newStorage;

public class ConnectedFileExclusionsMediumTest {

  private static final String SERVER_ID = "local";
  private static final String PROJECT_KEY = "test-project-2";
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static ConnectedSonarLintEngineImpl sonarlint;
  private static File baseDir;
  private static ProjectStorageFixture.ProjectStorage projectStorage;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    var storage = newStorage(SERVER_ID)
      .withProject(PROJECT_KEY)
      .create(slHome);
    projectStorage = storage.getProjectStorages().get(0);
    /*
     * This storage contains one server id "local" and two projects: "test-project" (with an empty QP) and "test-project-2" (with default
     * QP)
     */
    Path sampleStorage = Paths.get(ConnectedFileExclusionsMediumTest.class.getResource("/sample-storage").toURI());
    Path tmpStorage = storage.getPath();
    FileUtils.copyDirectory(sampleStorage.toFile(), tmpStorage.toFile());

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(tmpStorage)
      .setLogOutput(createNoOpLogOutput())
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  @AfterClass
  public static void stop() {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @Test
  public void fileInclusionsExclusions() throws Exception {
    ClientInputFile mainFile1 = prepareInputFile("foo.xoo", "function xoo() {}", false);
    ClientInputFile mainFile2 = prepareInputFile("src/foo2.xoo", "function xoo() {}", false);
    ClientInputFile testFile1 = prepareInputFile("fooTest.xoo", "function xoo() {}", true);
    ClientInputFile testFile2 = prepareInputFile("test/foo2Test.xoo", "function xoo() {}", true);

    int result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(4);

    storeProjectSettings(ImmutableMap.of("sonar.inclusions", "src/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(ImmutableMap.of("sonar.inclusions", "file:**/src/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(ImmutableMap.of("sonar.exclusions", "src/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(ImmutableMap.of("sonar.test.inclusions", "test/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(ImmutableMap.of("sonar.test.exclusions", "test/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(ImmutableMap.of("sonar.inclusions", "file:**/src/**", "sonar.test.exclusions", "**/*Test.*"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(1);
  }

  private void storeProjectSettings(Map<String, String> settings) {
    projectStorage.setSettings(settings);
  }

  private int count(ClientInputFile mainFile1, ClientInputFile mainFile2, ClientInputFile testFile1, ClientInputFile testFile2) {
    List<String> filePaths = Arrays.asList(mainFile1.getPath(), mainFile2.getPath(), testFile1.getPath(), testFile2.getPath());
    ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "", "");
    List<String> result = sonarlint.getExcludedFiles(projectBinding, filePaths, Function.identity(), f -> f.contains("Test"));
    return filePaths.size() - result.size();
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    ClientInputFile inputFile = TestUtils.createInputFile(file.toPath(), relativePath, isTest);
    return inputFile;
  }
}
