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

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class StandaloneNoPluginMediumTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private StandaloneSonarLintEngine sonarlint;
  private File baseDir;
  private final Multimap<LogOutput.Level, String> logs = LinkedListMultimap.create();

  @Before
  public void prepare() throws IOException {
    LogOutput logOutput = (msg, level) -> logs.put(level, msg);
    sonarlint = new StandaloneSonarLintEngineImpl(StandaloneGlobalConfiguration.builder()
      .setLogOutput(logOutput)
      .setModulesProvider(() -> singletonList(new ModuleInfo("key", mock(ClientFileSystem.class))))
      .build());

    baseDir = temp.newFolder();
  }

  @After
  public void stop() {
    sonarlint.stop();
  }

  @Test
  public void dont_fail_and_detect_language_even_if_no_plugin() throws Exception {

    assertThat(sonarlint.getPluginDetails()).isEmpty();

    ClientInputFile inputFile = prepareInputFile("foo.js", "function foo() {var x;}", false);

    AnalysisResults results = sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      i -> {
      }, null, null);

    assertThat(results.indexedFileCount()).isEqualTo(1);
    assertThat(results.languagePerFile()).containsEntry(inputFile, Language.JS);

    assertThat(sonarlint.getAllRuleDetails()).isEmpty();
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }

}
