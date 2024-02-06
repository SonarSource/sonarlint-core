/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.legacy.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import testutils.TestUtils;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StandaloneNoPluginMediumTests {

  private static final String CONFIGURATION_SCOPE_ID = "configScopeId";
  private SonarLintAnalysisEngine engine;

  @TempDir
  private File baseDir;
  private final Multimap<ClientLogOutput.Level, String> logs = LinkedListMultimap.create();

  @BeforeEach
  void prepare() {
    ClientLogOutput logOutput = (msg, level) -> logs.put(level, msg);
    engine = new SonarLintAnalysisEngine(EngineConfiguration.builder()
      .setLogOutput(logOutput)
      .setModulesProvider(() -> List.of(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .build(), newBackend().build(), null);
  }

  @AfterEach
  void stop() {
    engine.stop();
  }

  @Test
  void dont_fail_and_detect_language_even_if_no_plugin() throws Exception {

    assertThat(engine.getPluginDetails()).isEmpty();

    var inputFile = prepareInputFile("foo.js", "function foo() {var x;}", false);

    var results = engine.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      i -> {
      }, null, null, CONFIGURATION_SCOPE_ID);

    assertThat(results.indexedFileCount()).isEqualTo(1);
    assertThat(results.languagePerFile()).containsEntry(inputFile, SonarLanguage.JS);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final var file = new File(baseDir, relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }

}
