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
package mediumtests;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import testutils.OnDiskTestClientInputFile;
import testutils.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;

class StandaloneIssueMediumTests {

  private static Path sonarlintUserHome;
  private static Path fakeTypeScriptProjectPath;

  private static final String A_JAVA_FILE_PATH = "Foo.java";
  private static StandaloneSonarLintEngine sonarlint;
  private File baseDir;

  @BeforeAll
  static void prepare(@TempDir Path temp) throws Exception {
    sonarlintUserHome = temp.resolve("home");
    fakeTypeScriptProjectPath = temp.resolve("ts");

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

    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("sonar.typescript.internal.typescriptLocation", fakeTypeScriptProjectPath.resolve("node_modules").toString());
    // See test sonarjs_should_honor_global_and_analysis_level_properties
    extraProperties.put("sonar.javascript.globals", "GLOBAL1");

    NodeJsHelper nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    StandaloneGlobalConfiguration.Builder configBuilder = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginPath())
      .addPlugin(PluginLocator.getJavaPluginPath())
      .addPlugin(PluginLocator.getPhpPluginPath())
      .addPlugin(PluginLocator.getPythonPluginPath())
      .addEnabledLanguages(Language.JAVA, Language.PYTHON)
      .setSonarLintUserHome(sonarlintUserHome)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setExtraProperties(extraProperties);
    sonarlint = new StandaloneSonarLintEngine(configBuilder.build());
  }

  @AfterAll
  static void stop() throws IOException {
    sonarlint.stop();
  }

  @BeforeEach
  void prepareBasedir(@TempDir Path temp) throws Exception {
    baseDir = Files.createTempDirectory(temp, "baseDir").toFile();
  }

  @Test
  void analysisErrors() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function foo() {\n"
      + "  var xoo;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);
    prepareInputFile("foo.xoo.error", "1,2,error analysing\n2,3,error analysing", false);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null, null);
    assertThat(results.failedAnalysisFiles()).containsExactly(inputFile);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("xoo:HasTag", 2, 6, "foo.xoo"));
  }

  // SLCORE-162
  @Test
  void useRelativePathToEvaluatePathPatterns() throws Exception {

    final File file = new File(baseDir, "foo.tmp"); // Temporary file doesn't have the correct file suffix
    FileUtils.write(file, "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n", StandardCharsets.UTF_8);
    ClientInputFile inputFile = new OnDiskTestClientInputFile(file.toPath(), "foo.py", false, StandardCharsets.UTF_8, null);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add,
      null, null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("python:PrintStatementUsage", 2, "foo.py"));
  }

  // SLCORE-251
  @Test
  void noRuleTemplates() throws Exception {
    assertThat(sonarlint.getAllRuleDetails()).extracting(SonarLintRuleDefinition::getKey).contains("python:PrintStatementUsage").doesNotContain("python:XPath");
    assertThat(sonarlint.getRuleDetails("python:XPath")).isEmpty();
  }

  @Test
  void simpleJavaWithExcludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final Collection<String> excludedRules = List.of("java:S106");
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addExcludedRules(excludedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S1220", null, A_JAVA_FILE_PATH),
      tuple("java:S1481", 3, A_JAVA_FILE_PATH));
  }

  @Test
  void simpleJavaWithExcludedRulesUsingDeprecatedKey() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final Collection<String> excludedRules = List.of("squid:S106");
    List<String> logs = new ArrayList<>();
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addExcludedRules(excludedRules)
        .build(),
      issues::add, (msg, lvl) -> logs.add(msg), null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S1220", null, A_JAVA_FILE_PATH, "MINOR"),
      tuple("java:S1481", 3, A_JAVA_FILE_PATH, "MINOR"));

    assertThat(logs).contains("Rule 'java:S106' was excluded using its deprecated key 'squid:S106'. Please fix your configuration.");
  }

  @Test
  void simpleJavaWithIncludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}",
      false);

    final Collection<String> includedRules = List.of("java:S3553");
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRules(includedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S3553", 3, A_JAVA_FILE_PATH),
      tuple("java:S106", 5, A_JAVA_FILE_PATH),
      tuple("java:S1220", null, A_JAVA_FILE_PATH),
      tuple("java:S1481", 4, A_JAVA_FILE_PATH));
  }

  @Test
  void simpleJavaWithIncludedRulesUsingDeprecatedKey() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}",
      false);

    final Collection<String> includedRules = List.of("squid:S3553");
    List<String> logs = new CopyOnWriteArrayList<>();
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRules(includedRules)
        .build(),
      issues::add, (msg, lvl) -> logs.add(msg), null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S3553", 3, A_JAVA_FILE_PATH),
      tuple("java:S106", 5, A_JAVA_FILE_PATH),
      tuple("java:S1220", null, A_JAVA_FILE_PATH),
      tuple("java:S1481", 4, A_JAVA_FILE_PATH));

    assertThat(logs).contains("Rule 'java:S3553' was included using its deprecated key 'squid:S3553'. Please fix your configuration.");
  }

  @Test
  void simpleJavaWithIncludedAndExcludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:S3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}",
      false);

    // exclusion wins
    final Collection<String> excludedRules = List.of("squid:S3553");
    final Collection<String> includedRules = List.of("squid:S3553");
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addExcludedRules(excludedRules)
        .addIncludedRules(includedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, A_JAVA_FILE_PATH),
      tuple("java:S1220", null, A_JAVA_FILE_PATH),
      tuple("java:S1481", 4, A_JAVA_FILE_PATH));
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding, @Nullable Language language) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    return new OnDiskTestClientInputFile(file.toPath(), relativePath, isTest, encoding, language);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8, null);
  }

}
