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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.analysis.sonarapi.SonarLintModuleFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import testutils.OnDiskTestClientInputFile;
import testutils.PluginLocator;
import testutils.TestUtils;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.sonarsource.sonarlint.core.client.api.common.ClientFileSystemFixtures.aClientFileSystemWith;

class StandaloneIssueMediumTests {

  private static final CanceledProgressMonitor CANCELED_PROGRESS_MONITOR = new CanceledProgressMonitor();
  private static Path sonarlintUserHome;
  private static Path fakeTypeScriptProjectPath;

  private static final String A_JAVA_FILE_PATH = "Foo.java";
  private static StandaloneSonarLintEngineImpl sonarlint;
  private File baseDir;
  // commercial plugins might not be available
  // (if you pass -Dcommercial to maven, a profile will be activated that downloads the commercial plugins)
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;

  @BeforeAll
  static void prepare(@TempDir Path temp) throws Exception {
    sonarlintUserHome = temp.resolve("home");
    fakeTypeScriptProjectPath = temp.resolve("ts");

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

    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("sonar.typescript.internal.typescriptLocation", fakeTypeScriptProjectPath.resolve("node_modules").toString());
    // See test sonarjs_should_honor_global_and_analysis_level_properties
    extraProperties.put("sonar.javascript.globals", "GLOBAL1");

    var nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    var configBuilder = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginPath())
      .addPlugin(PluginLocator.getJavaPluginPath())
      .addPlugin(PluginLocator.getPhpPluginPath())
      .addPlugin(PluginLocator.getPythonPluginPath())
      .addPlugin(PluginLocator.getXmlPluginPath())
      .addEnabledLanguages(Language.JS, Language.JAVA, Language.PHP, Language.PYTHON, Language.TS, Language.C, Language.YAML, Language.XML)
      .setSonarLintUserHome(sonarlintUserHome)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setExtraProperties(extraProperties);

    if (COMMERCIAL_ENABLED) {
      configBuilder.addPlugin(PluginLocator.getCppPluginPath());
    }
    sonarlint = new StandaloneSonarLintEngineImpl(configBuilder.build());
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
  void simpleJavaScript() throws Exception {

    var ruleDetails = sonarlint.getRuleDetails("javascript:S1481").get();
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables and functions should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo(Language.JS);
    assertThat(ruleDetails.getDefaultSeverity()).isEqualTo(IssueSeverity.MINOR);
    assertThat(ruleDetails.getTags()).containsOnly("unused");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "If a local variable or a local function is declared but not used");

    var content = "function foo() {\n"
      + "  let x;\n"
      + "  let y; //NOSONAR\n"
      + "}";
    var inputFile = prepareInputFile("foo.js", content, false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null,
      null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getRuleDescriptionContextKey).containsOnly(
      tuple("javascript:S1481", 2, "foo.js", Optional.empty()));

    // SLCORE-160
    inputFile = prepareInputFile("node_modules/foo.js", content, false);

    issues.clear();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add, null,
      null);
    assertThat(issues).isEmpty();
  }

  @Test
  void sonarjs_should_honor_global_and_analysis_level_properties() throws Exception {
    var content = "function foo() {\n"
      + "  console.log(LOCAL1); // Noncompliant\n"
      + "  console.log(GLOBAL1); // GLOBAL1 defined as global variable in global settings\n"
      + "}";
    var inputFile = prepareInputFile("foo.js", content, false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRule(RuleKey.parse("javascript:S3827"))
        .build(),
      issues::add, (m, l) -> System.out.println(m),
      null);
    assertThat(issues.stream().filter(i -> i.getRuleKey().equals("javascript:S3827")))
      .extracting(Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
        tuple(2, "foo.js"));

    // Change globals using analysis property
    issues.clear();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .putExtraProperty("sonar.javascript.globals", "LOCAL1")
        .addIncludedRule(RuleKey.parse("javascript:S3827"))
        .build(),
      issues::add, null,
      null);
    assertThat(issues.stream().filter(i -> i.getRuleKey().equals("javascript:S3827")))
      .extracting(Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
        tuple(3, "foo.js"));
  }

  @Test
  void simpleTypeScript() throws Exception {
    var ruleDetails = sonarlint.getRuleDetails("typescript:S1764").get();
    assertThat(ruleDetails.getName()).isEqualTo("Identical expressions should not be used on both sides of a binary operator");
    assertThat(ruleDetails.getLanguage()).isEqualTo(Language.TS);
    assertThat(ruleDetails.getDefaultSeverity()).isEqualTo(IssueSeverity.MAJOR);
    assertThat(ruleDetails.getTags()).isEmpty();
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "Using the same value on either side of a binary operator is almost always a mistake");

    final var tsConfig = new File(baseDir, "tsconfig.json");
    FileUtils.write(tsConfig, "{}", StandardCharsets.UTF_8);

    var inputFile = prepareInputFile("foo.ts", "function foo() {\n"
      + "  if(bar() && bar()) { return 42; }\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add, null,
      null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("typescript:S1764", 2, "foo.ts"));

  }
  @Test
  void simpleJavaScriptInYamlFile() throws Exception {
    String content = "Resources:\n" +
            "  LambdaFunction:\n" +
            "    Type: 'AWS::Lambda::Function'\n" +
            "    Properties:\n" +
            "      Code:\n" +
            "        ZipFile: >\n" +
            "          exports.handler = function(event, context) {\n" +
            "            let x;\n" +
            "          };\n" +
            "      Runtime: nodejs8.10";

    var inputFile = prepareInputFile("foo.yaml", content, false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
            StandaloneAnalysisConfiguration.builder()
                    .setBaseDir(baseDir.toPath())
                    .addInputFile(inputFile)
                    .build(),
            issues::add, (s, level) -> System.out.println(s),
            null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
            tuple("javascript:S1481", 8, "foo.yaml"));
  }

  @Test
  void simpleC() throws Exception {
    assumeTrue(COMMERCIAL_ENABLED);
    // prepareInputFile("foo.h", "", false, StandardCharsets.UTF_8, Language.C);
    // prepareInputFile("foo2.h", "", false, StandardCharsets.UTF_8, Language.C);
    var inputFile = prepareInputFile("foo.c", "#import \"foo.h\"\n"
      + "#import \"foo2.h\" //NOSONAR\n", false, StandardCharsets.UTF_8, Language.C);

    var buildWrapperContent = "{\"version\":0,\"captures\":[" +
      "{" +
      "\"compiler\": \"clang\"," +
      "\"executable\": \"compiler\"," +
      "\"stdout\": \"#define __STDC_VERSION__ 201112L\n\"," +
      "\"stderr\": \"\"" +
      "}," +
      "{" +
      "\"compiler\": \"clang\"," +
      "\"executable\": \"compiler\"," +
      "\"stdout\": \"#define __cplusplus 201703L\n\"," +
      "\"stderr\": \"\"" +
      "}," +
      "{\"compiler\":\"clang\",\"cwd\":\"" +
      baseDir.toString().replace("\\", "\\\\") +
      "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"foo.c\"]}]}";

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .putExtraProperty("sonar.cfamily.build-wrapper-content", buildWrapperContent)
        .build(),
      issues::add, (m, l) -> System.out.println(m), null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("c:S3805", 1, 0, "foo.c"),
      // FIXME no sonar is not supported by the CFamily analyzer
      tuple("c:S3805", 2, 0, "foo.c"));
  }

  @Test
  void simplePhp() throws Exception {
    var inputFile = prepareInputFile("foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    $i = 0; // NOSONAR\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add,
      null, null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("php:S1172", 2, "foo.php"));
  }

  @Test
  void fileEncoding() throws IOException {
    var inputFile = prepareInputFile("foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>", false, StandardCharsets.UTF_16, null);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null, null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("php:S1172", 2, "foo.php"));
  }

  @Test
  void returnLanguagePerFile() throws IOException {
    var inputFile = prepareInputFile("foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>", false);

    final List<Issue> issues = new ArrayList<>();
    var results = sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null, null);
    assertThat(results.languagePerFile()).containsExactly(entry(inputFile, Language.PHP));
  }

  @Test
  void analysisErrors() throws Exception {
    var inputFile = prepareInputFile("foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    echo \"Hello world!;\n"
      + "}\n"
      + "?>", false);

    final List<Issue> issues = new ArrayList<>();
    var results = sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null, null);
    assertThat(results.failedAnalysisFiles()).containsExactly(inputFile);
    assertThat(issues).isEmpty();
  }

  @Test
  void simplePython() throws Exception {

    var inputFile = prepareInputFile("foo.py", "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add,
      null, null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("python:PrintStatementUsage", 2, "foo.py"));
  }

  // SLCORE-162
  @Test
  void useRelativePathToEvaluatePathPatterns() throws Exception {

    final var file = new File(baseDir, "foo.tmp"); // Temporary file doesn't have the correct file suffix
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

  @Test
  void simpleJava() throws Exception {
    var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    // TODO full line issue\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add,
      null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, Issue::getEndLine, Issue::getEndLineOffset,
      i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
        tuple("java:S1220", null, null, null, null, A_JAVA_FILE_PATH, IssueSeverity.MINOR),
        tuple("java:S1481", 3, 8, 3, 9, A_JAVA_FILE_PATH, IssueSeverity.MINOR),
        tuple("java:S106", 4, 4, 4, 14, A_JAVA_FILE_PATH, IssueSeverity.MAJOR),
        tuple("java:S1135", 5, 0, 5, 27, A_JAVA_FILE_PATH, IssueSeverity.INFO));
  }

  @Test
  void simpleJavaWithQuickFix() throws Exception {
    var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        +"     \n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(), issues::add,
      null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, Issue::getEndLine, Issue::getEndLineOffset,
      i -> i.getInputFile().relativePath(), Issue::getSeverity).contains(
      tuple("java:S1186", 2, 14, 2, 17, A_JAVA_FILE_PATH, IssueSeverity.CRITICAL));

    assertThat(issues)
      .flatExtracting(Issue::quickFixes)
      .extracting(QuickFix::message)
      .containsOnly("Insert placeholder comment");
    assertThat(issues)
      .flatExtracting(Issue::quickFixes)
      .flatExtracting(QuickFix::inputFileEdits)
      .extracting(ClientInputFileEdit::target)
      .containsOnly(inputFile);
    assertThat(issues)
      .flatExtracting(Issue::quickFixes)
      .flatExtracting(QuickFix::inputFileEdits)
      .flatExtracting(ClientInputFileEdit::textEdits)
      .extracting(TextEdit::range, TextEdit::newText)
      .containsOnly(
        tuple(new TextRange(2, 21, 4, 2), "\n    // TODO document why this method is empty\n  "));
  }

  @Test
  void simpleJavaWithCommaInClasspath() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .putExtraProperty("sonar.java.libraries", "\"" + Paths.get("target/lib/guava,with,comma.jar").toAbsolutePath().toString() + "\"")
      .build(), issues::add,
      null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, Issue::getEndLine, Issue::getEndLineOffset,
      i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
        tuple("java:S1220", null, null, null, null, A_JAVA_FILE_PATH, IssueSeverity.MINOR),
        tuple("java:S1481", 3, 8, 3, 9, A_JAVA_FILE_PATH, IssueSeverity.MINOR));
  }

  // SLCORE-251
  @Test
  void noRuleTemplates() throws Exception {
    assertThat(sonarlint.getAllRuleDetails()).extracting(RuleDetails::getKey).doesNotContain("python:XPath");
    assertThat(sonarlint.getRuleDetails("python:XPath")).isEmpty();
  }

  @Test
  void onlyLoadRulesOfEnabledLanguages() {
    Set<Language> enabledLanguages = EnumSet.of(
      Language.JAVA,
      Language.JS,
      Language.PHP,
      Language.PYTHON,
      Language.TS,
      Language.XML);

    if (COMMERCIAL_ENABLED) {
      enabledLanguages.add(Language.C);
    }
    assertThat(sonarlint.getAllRuleDetails().stream().map(RuleDetails::getLanguage))
      .hasSameElementsAs(enabledLanguages);
  }

  @Test
  void simpleJavaNoHotspots() throws Exception {
    assertThat(sonarlint.getAllRuleDetails()).extracting(RuleDetails::getKey).doesNotContain("java:S1313");
    assertThat(sonarlint.getRuleDetails("java:S1313")).isEmpty();

    var inputFile = prepareInputFile("foo/Foo.java",
      "package foo;\n"
        + "public class Foo {\n"
        + "  String ip = \"192.168.12.42\"; // Hotspots should not be reported in SonarLint\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRule(new RuleKey("java", "S1313"))
        .build(),
      issues::add,
      null, null);

    assertThat(issues).isEmpty();
  }

  @Test
  void simpleJavaPomXml() throws Exception {
    var inputFile = prepareInputFile("pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add,
      null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("xml:S3421", 6, "pom.xml", IssueSeverity.MINOR));
  }

  @Test
  void supportJavaSuppressWarning() throws Exception {
    var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  @SuppressWarnings(\"java:S106\")\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add,
      null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S1220", null, A_JAVA_FILE_PATH, IssueSeverity.MINOR),
      tuple("java:S1481", 4, A_JAVA_FILE_PATH, IssueSeverity.MINOR));
  }

  @Test
  void simpleJavaWithBytecode() throws Exception {
    var projectWithByteCode = new File("src/test/projects/java-with-bytecode").getAbsoluteFile().toPath();
    var inputFile = TestUtils.createInputFile(projectWithByteCode.resolve("src/Foo.java"), "src/Foo.java", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(projectWithByteCode)
        .addInputFile(inputFile)
        .putExtraProperty("sonar.java.binaries", projectWithByteCode.resolve("bin").toString())
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, "src/Foo.java"),
      tuple("java:S1220", null, "src/Foo.java"),
      tuple("java:S1144", 8, "src/Foo.java"),
      tuple("java:S1186", 8, "src/Foo.java"));
  }

  @Test
  void simpleJavaWithExcludedRules() throws Exception {
    var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final Collection<RuleKey> excludedRules = singleton(new RuleKey("java", "S106"));
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addExcludedRules(excludedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S1220", null, A_JAVA_FILE_PATH, IssueSeverity.MINOR),
      tuple("java:S1481", 3, A_JAVA_FILE_PATH, IssueSeverity.MINOR));
  }

  @Test
  void simpleJavaWithExcludedRulesUsingDeprecatedKey() throws Exception {
    var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final Collection<RuleKey> excludedRules = singleton(new RuleKey("squid", "S106"));
    List<String> logs = new ArrayList<>();
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addExcludedRules(excludedRules)
        .build(),
      issues::add, (msg, lvl) -> logs.add(msg), null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S1220", null, A_JAVA_FILE_PATH, IssueSeverity.MINOR),
      tuple("java:S1481", 3, A_JAVA_FILE_PATH, IssueSeverity.MINOR));

    assertThat(logs).contains("Rule 'java:S106' was excluded using its deprecated key 'squid:S106'. Please fix your configuration.");
  }

  @Test
  void simpleJavaWithIncludedRules() throws Exception {
    var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}",
      false);

    final Collection<RuleKey> includedRules = singleton(new RuleKey("java", "S3553"));
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRules(includedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S3553", 3, A_JAVA_FILE_PATH, IssueSeverity.MAJOR),
      tuple("java:S106", 5, A_JAVA_FILE_PATH, IssueSeverity.MAJOR),
      tuple("java:S1220", null, A_JAVA_FILE_PATH, IssueSeverity.MINOR),
      tuple("java:S1481", 4, A_JAVA_FILE_PATH, IssueSeverity.MINOR));
  }

  @Test
  void simpleJavaWithIncludedRulesUsingDeprecatedKey() throws Exception {
    var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}",
      false);

    final Collection<RuleKey> includedRules = singleton(new RuleKey("squid", "S3553"));
    List<String> logs = new CopyOnWriteArrayList<>();
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRules(includedRules)
        .build(),
      issues::add, (msg, lvl) -> logs.add(msg), null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S3553", 3, A_JAVA_FILE_PATH, IssueSeverity.MAJOR),
      tuple("java:S106", 5, A_JAVA_FILE_PATH, IssueSeverity.MAJOR),
      tuple("java:S1220", null, A_JAVA_FILE_PATH, IssueSeverity.MINOR),
      tuple("java:S1481", 4, A_JAVA_FILE_PATH, IssueSeverity.MINOR));

    assertThat(logs).contains("Rule 'java:S3553' was included using its deprecated key 'squid:S3553'. Please fix your configuration.");
  }

  @Test
  void simpleJavaWithIssueOnDir() throws Exception {
    var inputFile = prepareInputFile("foo/Foo.java",
      "package foo;\n"
        + "public class Foo {\n"
        + "}",
      false);

    final Collection<RuleKey> includedRules = singleton(new RuleKey("java", "S1228"));
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRules(includedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile() != null ? i.getInputFile().relativePath() : null, Issue::getSeverity).containsOnly(
      tuple("java:S2094", 2, "foo/Foo.java", IssueSeverity.MINOR),
      tuple("java:S1228", null, null, IssueSeverity.MINOR));
  }

  @Test
  void simpleJavaWithIncludedAndExcludedRules() throws Exception {
    var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:S3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}",
      false);

    // exclusion wins
    final Collection<RuleKey> excludedRules = Collections.singleton(new RuleKey("squid", "S3553"));
    final Collection<RuleKey> includedRules = Collections.singleton(new RuleKey("squid", "S3553"));
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addExcludedRules(excludedRules)
        .addIncludedRules(includedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 5, A_JAVA_FILE_PATH, IssueSeverity.MAJOR),
      tuple("java:S1220", null, A_JAVA_FILE_PATH, IssueSeverity.MINOR),
      tuple("java:S1481", 4, A_JAVA_FILE_PATH, IssueSeverity.MINOR));
  }

  @Test
  void testJavaSurefireDontCrashAnalysis() throws Exception {

    var surefireReport = new File(baseDir, "reports/TEST-FooTest.xml");
    FileUtils.write(surefireReport, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<testsuite name=\"FooTest\" time=\"0.121\" tests=\"1\" errors=\"0\" skipped=\"0\" failures=\"0\">\n" +
      "<testcase name=\"errorAnalysis\" classname=\"FooTest\" time=\"0.031\"/>\n" +
      "</testsuite>", StandardCharsets.UTF_8);

    var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    var inputFileTest = prepareInputFile("FooTest.java",
      "public class FooTest {\n"
        + "  public void testFoo() {\n"
        + "  }\n"
        + "}",
      true);

    final List<Issue> issues = new ArrayList<>();
    var results = sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile, inputFileTest)
        .putExtraProperty("sonar.junit.reportsPath", "reports/")
        .build(),
      issues::add, null, null);

    assertThat(results.indexedFileCount()).isEqualTo(2);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 4, A_JAVA_FILE_PATH),
      tuple("java:S1220", null, A_JAVA_FILE_PATH),
      tuple("java:S1481", 3, A_JAVA_FILE_PATH),
      tuple("java:S2187", 1, "FooTest.java"));
  }

  @Test
  void concurrentAnalysis() throws Throwable {
    final var inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    var parallelExecutions = 4;

    var executor = Executors.newFixedThreadPool(parallelExecutions);

    List<Future<?>> results = new ArrayList<>();
    for (var i = 0; i < parallelExecutions; i++) {

      Runnable worker = () -> sonarlint.analyze(
        StandaloneAnalysisConfiguration.builder()
          .setBaseDir(baseDir.toPath())
          .addInputFile(inputFile)
          .build(),
        issue -> {
        }, null, null);
      results.add(executor.submit(worker));
    }
    executor.shutdown();

    while (!executor.isTerminated()) {
    }

    for (Future<?> future : results) {
      try {
        future.get();
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    }
  }

  @Test
  void lazy_init_file_metadata() throws Exception {
    final var inputFile1 = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);
    var unexistingPath = new File(baseDir, "missing.bin");
    assertThat(unexistingPath).doesNotExist();
    ClientInputFile inputFile2 = new OnDiskTestClientInputFile(unexistingPath.toPath(), "missing.bin", false, StandardCharsets.UTF_8, null);

    final List<Issue> issues = new ArrayList<>();
    final List<String> logs = new CopyOnWriteArrayList<>();
    var analysisResults = sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile1, inputFile2)
        .build(),
      issues::add,
      (m, l) -> logs.add(m), null);

    assertThat(analysisResults.failedAnalysisFiles()).isEmpty();
    assertThat(analysisResults.indexedFileCount()).isEqualTo(2);
    assertThat(logs)
      .contains("Initializing metadata of file " + inputFile1.uri())
      .doesNotContain("Initializing metadata of file " + inputFile2.uri());
  }

  @Test
  void declare_module_should_create_a_module_container_with_loaded_extensions() throws Exception {
    sonarlint
      .declareModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null)))).get();

    ModuleContainer moduleContainer = sonarlint.getAnalysisEngine().getModuleRegistry().getContainerFor("key");

    assertThat(moduleContainer).isNotNull();
    assertThat(moduleContainer.getComponentsByType(SonarLintModuleFileSystem.class)).isNotEmpty();
  }

  @Test
  void stop_module_should_stop_the_module_container() throws Exception {
    sonarlint
      .declareModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null)))).get();
    ModuleContainer moduleContainer = sonarlint.getAnalysisEngine().getModuleRegistry().getContainerFor("key");

    sonarlint.stopModule("key").get();

    assertThat(moduleContainer.getSpringContext().isActive()).isFalse();
  }

  @Test
  void shouldThrowCancelExceptionWhenCanceled() throws Exception {
    var inputFile = prepareInputFile("foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    $i = 0; // NOSONAR\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>", false);

    final List<Issue> issues = new ArrayList<>();
    StandaloneAnalysisConfiguration analysisConfiguration = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build();
    assertThrows(CanceledException.class, () -> sonarlint.analyze(analysisConfiguration, issues::add, null, CANCELED_PROGRESS_MONITOR));
  }

  private static final class CanceledProgressMonitor implements ClientProgressMonitor {
    @Override
    public boolean isCanceled() {
      return true;
    }

    @Override
    public void setMessage(String msg) {
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
    }

    @Override
    public void setFraction(float fraction) {
    }
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding, @Nullable Language language) throws IOException {
    final var file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    return new OnDiskTestClientInputFile(file.toPath(), relativePath, isTest, encoding, language);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8, null);
  }

}
