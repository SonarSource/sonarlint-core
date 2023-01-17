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
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason;
import testutils.OnDiskTestClientInputFile;
import testutils.PluginLocator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

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
    var packagejson = fakeTypeScriptProjectPath.resolve("package.json");
    FileUtils.write(packagejson.toFile(), "{"
      + "\"devDependencies\": {\n" +
      "    \"typescript\": \"2.6.1\"\n" +
      "  }"
      + "}", StandardCharsets.UTF_8);
    var pb = new ProcessBuilder("npm" + (SystemUtils.IS_OS_WINDOWS ? ".cmd" : ""), "install")
      .directory(fakeTypeScriptProjectPath.toFile())
      .inheritIO();
    var process = pb.start();
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
    var configBuilder = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginPath())
      .addEnabledLanguages(Language.JS, Language.TS)
      .setSonarLintUserHome(sonarlintUserHome)
      .setNodeJs(Paths.get("wrong"), Version.create("13.0"))
      .setModulesProvider(() -> singletonList(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .setLogOutput((msg, level) -> logs.add(msg));

    sonarlint = new StandaloneSonarLintEngineImpl(configBuilder.build());

    var ruleDetails = sonarlint.getRuleDetails(JAVASCRIPT_S1481).get();
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables and functions should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo(Language.JS);
    assertThat(ruleDetails.getDefaultSeverity()).isEqualTo(IssueSeverity.MINOR);
    assertThat(ruleDetails.getTags()).containsOnly("unused");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "If a local variable or a local function is declared but not used");

    final var issues = analyze();
    assertThat(issues).isEmpty();

    assertThat(logs).contains("Provided Node.js executable file does not exist. Property 'sonar.nodejs.executable' was set to 'wrong'");
  }

  @Test
  void unsatisfied_node_version() throws Exception {
    List<String> logs = new ArrayList<>();
    var configBuilder = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginPath())
      .addEnabledLanguages(Language.JS, Language.TS)
      .setSonarLintUserHome(sonarlintUserHome)
      .setNodeJs(Paths.get("node"), Version.create("1.0"))
      .setModulesProvider(() -> singletonList(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .setLogOutput((msg, level) -> logs.add(msg));

    sonarlint = new StandaloneSonarLintEngineImpl(configBuilder.build());

    assertThat(logs).contains("Plugin 'JavaScript/TypeScript/CSS Code Quality and Security' requires Node.js 12.22.0 while current is 1.0. Skip loading it.");
    var skipReason = sonarlint.getPluginDetails().stream().filter(p -> p.key().equals("javascript")).findFirst().get().skipReason();
    assertThat(skipReason).isNotEmpty();
    assertThat(skipReason.get()).isInstanceOf(SkipReason.UnsatisfiedRuntimeRequirement.class);
    var unsatisfiedNode = (SkipReason.UnsatisfiedRuntimeRequirement) skipReason.get();
    assertThat(unsatisfiedNode.getRuntime()).isEqualTo(SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS);
    assertThat(unsatisfiedNode.getCurrentVersion()).isEqualTo("1.0");
    assertThat(unsatisfiedNode.getMinVersion()).isEqualTo("12.22.0");
    assertThat(sonarlint.getRuleDetails(JAVASCRIPT_S1481)).isEmpty();

    final var issues = analyze();
    assertThat(issues).isEmpty();

  }

  private List<Issue> analyze() throws IOException {
    var content = "function foo() {\n"
      + "  var x;\n"
      + "  var y; //NOSONAR\n"
      + "}";
    var inputFile = prepareInputFile("foo.js", content, false);

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
    final var file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, encoding);
    return new OnDiskTestClientInputFile(file.toPath(), relativePath, isTest, encoding, language);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8, null);
  }

}
