/*
 * SonarLint Core - Analysis Engine
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.AnalysisEngine;
import org.sonarsource.sonarlint.core.GlobalAnalysisConfiguration;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpIssueListener;

public class LogMediumTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private AnalysisEngine sonarlint;
  private File baseDir;
  private Map<LogOutput.Level, Queue<String>> logs;

  @Before
  public void prepare() throws IOException {
    logs = new ConcurrentHashMap<>();
    GlobalAnalysisConfiguration config = GlobalAnalysisConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginUrl())
      .setLogOutput(createLogOutput(logs))
      .setModulesProvider(() -> List.of(new ModuleInfo("key", mock(ClientFileSystem.class))))
      .build();
    sonarlint = new AnalysisEngine(config);

    baseDir = temp.newFolder();
  }

  @After
  public void stop() {
    sonarlint.stop();
  }

  private LogOutput createLogOutput(final Map<LogOutput.Level, Queue<String>> logs) {
    return new LogOutput() {
      @Override
      public void log(String formattedMessage, Level level) {
        logs.computeIfAbsent(level, l -> new ConcurrentLinkedQueue<>()).add(formattedMessage);
      }
    };
  }

  private AnalysisConfiguration createConfig(ClientInputFile inputFile) throws IOException {
    return AnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build();
  }

  /**
   * If this test starts to fail randomly, check if any other test class in the core module is using {@link org.sonar.api.utils.log.LogTester} without
   * setting the root level back to debug in @AfterClass!
   */
  @Test
  public void changeLogOutputForAnalysis() throws Exception {
    logs.clear();
    ClientInputFile inputFile = prepareInputFile("foo.js", "function foo() {var x;}", false);
    sonarlint.analyze(createConfig(inputFile), createNoOpIssueListener(), null, null);
    assertThat(logs.get(LogOutput.Level.DEBUG)).isNotEmpty();
    logs.clear();

    final Map<LogOutput.Level, Queue<String>> logs2 = new ConcurrentHashMap<>();

    sonarlint.analyze(createConfig(inputFile), createNoOpIssueListener(), createLogOutput(logs2), null);
    assertThat(logs.get(LogOutput.Level.DEBUG)).isNullOrEmpty();
    assertThat(logs2.get(LogOutput.Level.DEBUG)).isNotEmpty();
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }
}
