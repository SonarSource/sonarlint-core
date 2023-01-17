/*
 * SonarLint Core - Implementation
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
package mediumtest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import mediumtest.fixtures.ProjectStorageFixture;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import testutils.TestUtils;

import static mediumtest.fixtures.StorageFixture.newStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static testutils.TestUtils.createNoOpLogOutput;

class ConnectedFileExclusionsMediumTests {

  private static final String SERVER_ID = "local";
  private static final String PROJECT_KEY = "test-project-2";
  private static ConnectedSonarLintEngineImpl sonarlint;

  @TempDir
  private static File baseDir;
  private static ProjectStorageFixture.ProjectStorage projectStorage;

  @BeforeAll
  static void prepare(@TempDir Path slHome) throws Exception {
    var storage = newStorage(SERVER_ID)
      .withProject(PROJECT_KEY)
      .create(slHome);
    projectStorage = storage.getProjectStorages().get(0);

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput(createNoOpLogOutput())
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
  }

  @AfterAll
  static void stop() {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @Test
  void fileInclusionsExclusions() throws Exception {
    var mainFile1 = prepareInputFile("foo.xoo", "function xoo() {}", false);
    var mainFile2 = prepareInputFile("src/foo2.xoo", "function xoo() {}", false);
    var testFile1 = prepareInputFile("fooTest.xoo", "function xoo() {}", true);
    var testFile2 = prepareInputFile("test/foo2Test.xoo", "function xoo() {}", true);

    var result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(4);

    storeProjectSettings(Map.of("sonar.inclusions", "src/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(Map.of("sonar.inclusions", "file:**/src/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(Map.of("sonar.exclusions", "src/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(Map.of("sonar.test.inclusions", "test/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(Map.of("sonar.test.exclusions", "test/**"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(3);

    storeProjectSettings(Map.of("sonar.inclusions", "file:**/src/**", "sonar.test.exclusions", "**/*Test.*"));
    result = count(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result).isEqualTo(1);
  }

  private void storeProjectSettings(Map<String, String> settings) {
    projectStorage.setSettings(settings);
  }

  private int count(ClientInputFile mainFile1, ClientInputFile mainFile2, ClientInputFile testFile1, ClientInputFile testFile2) {
    List<String> filePaths = Arrays.asList(mainFile1.getPath(), mainFile2.getPath(), testFile1.getPath(), testFile2.getPath());
    var projectBinding = new ProjectBinding(PROJECT_KEY, "", "");
    List<String> result = sonarlint.getExcludedFiles(projectBinding, filePaths, Function.identity(), f -> f.contains("Test"));
    return filePaths.size() - result.size();
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final var file = new File(baseDir, relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    var inputFile = TestUtils.createInputFile(file.toPath(), relativePath, isTest);
    return inputFile;
  }
}
