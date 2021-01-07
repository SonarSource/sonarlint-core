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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.OnDiskTestClientInputFile;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.UnsatisfiedRuntimeRequirement;
import org.sonarsource.sonarlint.core.client.api.common.Version;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

class StandaloneNodeJsTests {

  private static final String JAVASCRIPT_S1481 = "javascript:S1481";
  private StandaloneSonarLintEngineImpl sonarlint;

  private Path sonarlintUserHome;
  private Path fakeTypeScriptProjectPath;
  private Path baseDir;

  @BeforeEach
  void prepare(@TempDir Path temp) throws Exception {
    sonarlintUserHome = temp.resolve("home");
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
    StandaloneGlobalConfiguration.Builder configBuilder = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginUrl())
      .addEnabledLanguages(Language.JS, Language.TS)
      .setSonarLintUserHome(sonarlintUserHome)
      .setNodeJs(Paths.get("wrong"), Version.create("12.0"))
      .setLogOutput((msg, level) -> logs.add(msg));

    sonarlint = new StandaloneSonarLintEngineImpl(configBuilder.build());

    StandaloneRuleDetails ruleDetails = sonarlint.getRuleDetails(JAVASCRIPT_S1481).get();
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables and functions should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo(Language.JS);
    assertThat(ruleDetails.getSeverity()).isEqualTo("MINOR");
    assertThat(ruleDetails.getTags()).containsOnly("unused");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "If a local variable or a local function is declared but not used");

    final List<Issue> issues = analyze();
    assertThat(issues).isEmpty();

    assertThat(logs).contains("Provided Node.js executable file does not exist. Property 'sonar.nodejs.executable' was to 'wrong'");
  }

  @Test
  void unsatisfied_node_version() throws Exception {
    List<String> logs = new ArrayList<>();
    StandaloneGlobalConfiguration.Builder configBuilder = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginUrl())
      .addEnabledLanguages(Language.JS, Language.TS)
      .setSonarLintUserHome(sonarlintUserHome)
      .setNodeJs(Paths.get("node"), Version.create("1.0"))
      .setLogOutput((msg, level) -> logs.add(msg));

    sonarlint = new StandaloneSonarLintEngineImpl(configBuilder.build());

    assertThat(logs).contains("Plugin 'JavaScript/TypeScript Code Quality and Security' requires Node.js 8.0.0 while current is 1.0. Skip loading it.");
    Optional<SkipReason> skipReason = sonarlint.getPluginDetails().stream().filter(p -> p.key().equals("javascript")).findFirst().get().skipReason();
    assertThat(skipReason).isNotEmpty();
    assertThat(skipReason.get()).isInstanceOf(SkipReason.UnsatisfiedRuntimeRequirement.class);
    UnsatisfiedRuntimeRequirement unsatisfiedNode = (SkipReason.UnsatisfiedRuntimeRequirement) skipReason.get();
    assertThat(unsatisfiedNode.getRuntime()).isEqualTo(SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS);
    assertThat(unsatisfiedNode.getCurrentVersion()).isEqualTo("1.0");
    assertThat(unsatisfiedNode.getMinVersion()).isEqualTo("8.0.0");
    assertThat(sonarlint.getRuleDetails(JAVASCRIPT_S1481)).isEmpty();

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
      StandaloneAnalysisConfiguration.builder()
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
