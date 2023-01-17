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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;
import testutils.TestUtils;

import static mediumtest.fixtures.StorageFixture.newStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectedStorageProblemsMediumTests {

  private ConnectedSonarLintEngine sonarlint;

  @AfterEach
  void stop() {
    sonarlint.stop(true);
  }

  @Test
  void test_no_storage(@TempDir Path slHome, @TempDir Path baseDir) {

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("localhost")
      .setSonarLintUserHome(slHome)
      .setLogOutput((msg, level) -> {
      })
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    var serverBranchesError = assertThrows(StorageException.class, () -> sonarlint.getServerBranches("foo"));
    assertThat(serverBranchesError).hasMessageContaining("Failed to read file");

    var thrown = assertThrows(IllegalStateException.class, () -> sonarlint.getActiveRuleDetails(null, null, "rule", null));
    assertThat(thrown).hasMessage("Unable to find rule details for 'rule'");

    var analysisConfig = ConnectedAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .setProjectKey("myProject")
      .build();

    var thrown2 = assertThrows(StorageException.class, () -> sonarlint.analyze(analysisConfig, i -> {
    }, null, null));
    assertThat(thrown2).hasMessageContaining("Failed to read file");
  }

  @Test
  void corrupted_plugin_should_not_prevent_startup(@TempDir Path slHome, @TempDir Path baseDir) throws Exception {
    var storageId = "localhost";
    var storage = newStorage(storageId)
      .withJSPlugin()
      .withJavaPlugin()
      .withProject("myProject",
        project -> project.withRuleSet(Language.JS.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("java:S106", "BLOCKER")))
      .create(slHome);

    var cachedJSPlugin = storage.getPluginPaths().stream().filter(pluginPath -> pluginPath.getFileName().toString().contains("javascript")).findFirst().get();

    FileUtils.write(cachedJSPlugin.toFile(), "corrupted jar", StandardCharsets.UTF_8);

    List<String> logs = new CopyOnWriteArrayList<>();

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId(storageId)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput((m, l) -> logs.add(m))
      .addEnabledLanguage(Language.JAVA)
      .addEnabledLanguage(Language.JS)
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    assertThat(logs).contains("Unable to load plugin " + cachedJSPlugin);

    var inputFile = prepareJavaInputFile(baseDir);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setProjectKey("myProject")
      .setBaseDir(baseDir)
      .addInputFile(inputFile).build(),
      issues::add, null, null);

    assertThat(logs).contains("Execute Sensor: JavaSensor");
  }

  private ClientInputFile prepareJavaInputFile(Path baseDir) throws IOException {
    return prepareInputFile(baseDir, "Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareInputFile(Path baseDir, String relativePath, String content, final boolean isTest) throws IOException {
    final var file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    var inputFile = TestUtils.createInputFile(file.toPath(), relativePath, isTest);
    return inputFile;
  }

}
