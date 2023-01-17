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

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import testutils.PluginLocator;
import testutils.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static testutils.TestUtils.createNoOpIssueListener;

class LogMediumTests {

  private StandaloneSonarLintEngine sonarlint;

  @TempDir
  private File baseDir;
  private Multimap<ClientLogOutput.Level, String> logs;

  @BeforeEach
  void prepare() throws IOException {
    logs = Multimaps.synchronizedListMultimap(LinkedListMultimap.create());
    var config = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginPath())
      .setLogOutput(createLogOutput(logs))
      .setModulesProvider(() -> List.of(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .build();
    sonarlint = new StandaloneSonarLintEngineImpl(config);
  }

  @AfterEach
  void stop() {
    sonarlint.stop();
  }

  private ClientLogOutput createLogOutput(final Multimap<ClientLogOutput.Level, String> logs) {
    return new ClientLogOutput() {
      @Override
      public void log(String formattedMessage, Level level) {
        logs.put(level, formattedMessage);
      }
    };
  }

  private StandaloneAnalysisConfiguration createConfig(ClientInputFile inputFile) {
    return StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build();
  }

  /**
   * If this test starts to fail randomly, check if any other test class in the core module is using {@link org.sonar.api.utils.log.LogTester} without
   * setting the root level back to debug in @AfterClass!
   */
  @Test
  void changeLogOutputForAnalysis() throws IOException {
    logs.clear();
    var inputFile = prepareInputFile("foo.js", "function foo() {var x;}");
    sonarlint.analyze(createConfig(inputFile), createNoOpIssueListener(), null, null);
    assertThat(logs.get(ClientLogOutput.Level.DEBUG)).isNotEmpty();
    logs.clear();

    final Multimap<ClientLogOutput.Level, String> logs2 = Multimaps.synchronizedListMultimap(LinkedListMultimap.create());

    sonarlint.analyze(createConfig(inputFile), createNoOpIssueListener(), createLogOutput(logs2), null);
    assertThat(logs.get(ClientLogOutput.Level.DEBUG)).isEmpty();
    assertThat(logs2.get(ClientLogOutput.Level.DEBUG)).isNotEmpty();
  }

  private ClientInputFile prepareInputFile(String relativePath, String content) throws IOException {
    final var file = new File(baseDir, relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, false);
  }
}
