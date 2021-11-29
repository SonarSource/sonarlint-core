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
package mediumtests;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import org.sonarsource.sonarlint.core.plugin.common.Version;
import testutils.OnDiskTestClientInputFile;
import testutils.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

class StandaloneNodeJsTests {

  private AnalysisEngine sonarlint;

  private Path workDir;
  private Path fakeTypeScriptProjectPath;
  private Path baseDir;

  @BeforeEach
  void prepare(@TempDir Path temp) throws Exception {
    workDir = temp.resolve("workDir");
    fakeTypeScriptProjectPath = temp.resolve("ts");
    baseDir = temp.resolve("basedir");
    Path packagejson = fakeTypeScriptProjectPath.resolve("package.json");
    FileUtils.write(packagejson.toFile(), "{"
      + "\"devDependencies\": {\n" +
      "    \"typescript\": \"2.6.1\"\n" +
      "  }"
      + "}", StandardCharsets.UTF_8);
    ProcessBuilder pb = new ProcessBuilder("npm" + (SystemUtils.IS_OS_WINDOWS ? ".cmd" : ""), "install")
      .directory(fakeTypeScriptProjectPath.toFile())
      .inheritIO();
    Process process = pb.start();
    if (process.waitFor() != 0) {
      fail("Unable to run npm install");
    }

  }

  @AfterEach
  void stop() throws IOException {
    sonarlint.stop();
  }

  @Test
  void wrong_node_path() throws Exception {
    List<String> logs = new ArrayList<>();
    AnalysisEngineConfiguration.Builder configBuilder = AnalysisEngineConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginPath())
      .addEnabledLanguages(Language.JS, Language.TS)
      .setWorkDir(workDir)
      .setNodeJs(Paths.get("wrong"), Version.create("12.0"))
      .setLogOutput((msg, level) -> logs.add(msg));

    sonarlint = new AnalysisEngine(configBuilder.build());

    final List<Issue> issues = analyze();
    assertThat(issues).isEmpty();

    assertThat(logs).contains("Provided Node.js executable file does not exist. Property 'sonar.nodejs.executable' was to 'wrong'");
  }

  @Test
  void unsatisfied_node_version() throws Exception {
    List<String> logs = new ArrayList<>();
    AnalysisEngineConfiguration.Builder configBuilder = AnalysisEngineConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginPath())
      .addEnabledLanguages(Language.JS, Language.TS)
      .setWorkDir(workDir)
      .setNodeJs(Paths.get("node"), Version.create("1.0"))
      .setLogOutput((msg, level) -> logs.add(msg));

    sonarlint = new AnalysisEngine(configBuilder.build());

    assertThat(logs).contains("Plugin 'JavaScript/TypeScript Code Quality and Security' requires Node.js 8.0.0 while current is 1.0. Skip loading it.");

    final List<Issue> issues = analyze();
    assertThat(issues).isEmpty();

  }

  private List<Issue> analyze() throws IOException {
    String content = "function foo() {\n"
      + "  var x;\n"
      + "  var y; //NOSONAR\n"
      + "}";
    ClientInputFile inputFile = prepareInputFile("foo.js", content, false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir)
        .addInputFile(inputFile)
        .build(),
      issues::add, null,
      null);
    return issues;
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding, @Nullable Language language) throws IOException {
    final File file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, encoding);
    return new OnDiskTestClientInputFile(file.toPath(), relativePath, isTest, encoding, language);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8, null);
  }

}
