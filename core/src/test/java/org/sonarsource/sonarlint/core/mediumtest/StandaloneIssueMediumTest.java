/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.TestClientInputFile;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class StandaloneIssueMediumTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static StandaloneSonarLintEngineImpl sonarlint;
  private File baseDir;
  // commercial plugins might not be available
  // (if you pass -Dcommercial to maven, a profile will be activated that downloads the commercial plugins)
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;

  @BeforeClass
  public static void prepare() throws Exception {
    Path sonarlintUserHome = temp.newFolder().toPath();

    Path fakeTypeScriptProjectPath = temp.newFolder().toPath();
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
    StandaloneGlobalConfiguration.Builder configBuilder = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginUrl())
      .addPlugin(PluginLocator.getJavaPluginUrl())
      .addPlugin(PluginLocator.getPhpPluginUrl())
      .addPlugin(PluginLocator.getPythonPluginUrl())
      .addPlugin(PluginLocator.getXooPluginUrl())
      .addPlugin(PluginLocator.getTypeScriptPluginUrl())
      .setSonarLintUserHome(sonarlintUserHome)
      .setLogOutput((msg, level) -> System.out.println(msg))
      .setExtraProperties(extraProperties);

    if (COMMERCIAL_ENABLED) {
      configBuilder.addPlugin(PluginLocator.getCppPluginUrl());
    }
    sonarlint = new StandaloneSonarLintEngineImpl(configBuilder.build());
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop();
  }

  @Before
  public void prepareBasedir() throws Exception {
    baseDir = temp.newFolder();
  }

  @Test
  public void simpleJavaScript() throws Exception {

    RuleDetails ruleDetails = sonarlint.getRuleDetails("javascript:UnusedVariable").get();
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables and functions should be removed");
    assertThat(ruleDetails.getLanguageKey()).isEqualTo("js");
    assertThat(ruleDetails.getSeverity()).isEqualTo("MINOR");
    assertThat(ruleDetails.getTags()).containsOnly("unused");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "If a local variable or a local function is declared but not used");

    String content = "function foo() {\n"
      + "  var x;\n"
      + "  var y; //NOSONAR\n"
      + "}";
    ClientInputFile inputFile = prepareInputFile("foo.js", content, false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null,
      null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("javascript:UnusedVariable", 2, inputFile.getPath()));

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
  public void sonarjs_should_honor_global_and_analysis_level_properties() throws Exception {
    String content = "function foo() {\n"
      + "  console.log(LOCAL1); // Noncompliant\n"
      + "  console.log(GLOBAL1); // GLOBAL1 defined as global varibale in global settings\n"
      + "}";
    ClientInputFile inputFile = prepareInputFile("foo.js", content, false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRule(RuleKey.parse("javascript:S3827"))
        .build(),
      issues::add, null,
      null);
    assertThat(issues.stream().filter(i -> i.getRuleKey().equals("javascript:S3827")))
      .extracting("startLine", "inputFile.path").containsOnly(
        tuple(2, inputFile.getPath()));

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
      .extracting("startLine", "inputFile.path").containsOnly(
        tuple(3, inputFile.getPath()));
  }

  @Test
  public void simpleTypeScript() throws Exception {
    RuleDetails ruleDetails = sonarlint.getRuleDetails("typescript:S1764").get();
    assertThat(ruleDetails.getName()).isEqualTo("Identical expressions should not be used on both sides of a binary operator");
    assertThat(ruleDetails.getLanguageKey()).isEqualTo("ts");
    assertThat(ruleDetails.getSeverity()).isEqualTo("MAJOR");
    assertThat(ruleDetails.getTags()).containsOnly("cert");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "Using the same value on either side of a binary operator is almost always a mistake");

    final File tsConfig = new File(baseDir, "tsconfig.json");
    FileUtils.write(tsConfig, "{}", StandardCharsets.UTF_8);

    ClientInputFile inputFile = prepareInputFile("foo.ts", "function foo() {\n"
      + "  if(bar() && bar()) { return 42; }\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add, null,
      null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("typescript:S1764", 2, inputFile.getPath()));

  }

  @Test
  public void fileEncoding() throws IOException {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function xoo() {\n"
      + "  var xoo1, xoo2;\n"
      + "  var xoo; //NOSONAR\n"
      + "}", false, StandardCharsets.UTF_16, null);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("xoo:HasTag", 1, 9, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 6, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 12, inputFile.getPath()));
  }

  @Test
  public void simpleXoo() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function xoo() {\n"
      + "  var xoo1, xoo2;\n"
      + "  var xoo; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("xoo:HasTag", 1, 9, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 6, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 12, inputFile.getPath()));
  }

  @Test
  public void simpleC() throws Exception {
    assumeTrue(COMMERCIAL_ENABLED);
    ClientInputFile inputFile = prepareInputFile("foo.c", "#import \"foo.h\"\n"
      + "#import \"foo2.h\" //NOSONAR\n", false, StandardCharsets.UTF_8, "c");

    FileUtils.write(
      new File(baseDir, "build-wrapper-dump.json"),
      "{\"version\":0,\"captures\":[" +
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
        "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"foo.c\"]}]}",
      StandardCharsets.UTF_8);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .putExtraProperty("sonar.cfamily.build-wrapper-output", baseDir.getAbsolutePath())
        .build(),
      issues::add, null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("c:S3805", 1, 0, inputFile.getPath()));
  }

  @Test
  public void analysisErrors() throws Exception {
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
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("xoo:HasTag", 2, 6, inputFile.getPath()));
  }

  @Test
  public void returnLanguagePerFile() throws IOException {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function foo() {\n"
      + "  var xoo;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null, null);
    assertThat(results.languagePerFile()).containsExactly(entry(inputFile, "xoo"));
  }

  @Test
  public void simplePhp() throws Exception {

    ClientInputFile inputFile = prepareInputFile("foo.php", "<?php\n"
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
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("php:S1172", 2, inputFile.getPath()));
  }

  @Test
  public void simplePython() throws Exception {

    ClientInputFile inputFile = prepareInputFile("foo.py", "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add,
      null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("python:PrintStatementUsage", 2, inputFile.getPath()));
  }

  // SLCORE-162
  @Test
  public void useRelativePathToEvaluatePathPatterns() throws Exception {

    final File file = new File(baseDir, "foo.tmp"); // Temporary file doesn't have the correct file suffix
    FileUtils.write(file, "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n", StandardCharsets.UTF_8);
    ClientInputFile inputFile = new TestClientInputFile(file.toPath(), "foo.py", false, StandardCharsets.UTF_8, null);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add,
      null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("python:PrintStatementUsage", 2, inputFile.getPath()));
  }

  @Test
  public void simpleJava() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add,
      null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile.getPath(), "MINOR"),
      tuple("squid:S106", 4, inputFile.getPath(), "MAJOR"));
  }

  // SLCORE-251
  @Test
  public void noRuleTemplates() throws Exception {
    assertThat(sonarlint.getAllRuleDetails()).extracting(RuleDetails::getKey).doesNotContain("python:XPath", "xoo:xoo-template");
    assertThat(sonarlint.getRuleDetails("python:XPath")).isEmpty();
    assertThat(sonarlint.getRuleDetails("xoo:xoo-template")).isEmpty();
  }

  @Test
  public void simpleJavaNoHotspots() throws Exception {
    assertThat(sonarlint.getAllRuleDetails()).extracting(RuleDetails::getKey).doesNotContain("squid:S1313");
    List<Map.Entry<String, String>> ossLanguages = Arrays.asList(
      entry("java", "Java"),
      entry("js", "JavaScript"),
      entry("php", "PHP"),
      entry("py", "Python"),
      entry("ts", "TypeScript"),
      entry("xoo", "Xoo"),
      entry("xoo2", "Xoo2"));

    if (COMMERCIAL_ENABLED) {
      List<Map.Entry<String, String>> commercialLanguages = Arrays.asList(
        entry("c", "C"),
        entry("cpp", "C++"),
        entry("objc", "Objective-C"));
      assertThat(sonarlint.getAllLanguagesNameByKey()).containsOnly(Stream.concat(ossLanguages.stream(), commercialLanguages.stream())
        .toArray(Map.Entry[]::new));
    } else {
      assertThat(sonarlint.getAllLanguagesNameByKey()).containsOnly(ossLanguages.toArray(new Map.Entry[0]));
    }

    assertThat(sonarlint.getRuleDetails("squid:S1313")).isEmpty();

    ClientInputFile inputFile = prepareInputFile("foo/Foo.java",
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
        .addIncludedRule(new RuleKey("squid", "S1313"))
        .build(),
      issues::add,
      null, null);

    assertThat(issues).isEmpty();
  }

  @Test
  public void simpleJavaPomXml() throws Exception {
    ClientInputFile inputFile = prepareInputFile("pom.xml",
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

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S3421", 6, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void supportJavaSuppressWarning() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  @SuppressWarnings(\"squid:S106\")\n"
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

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 4, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simpleJavaWithBytecode() throws Exception {
    Path projectWithByteCode = new File("src/test/projects/java-with-bytecode").getAbsoluteFile().toPath();
    ClientInputFile inputFile = TestUtils.createInputFile(projectWithByteCode.resolve("src/Foo.java"), "src/Foo.java", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(projectWithByteCode)
        .addInputFile(inputFile)
        .putExtraProperty("sonar.java.binaries", projectWithByteCode.resolve("bin").toString())
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("squid:S106", 5, inputFile.getPath()),
      tuple("squid:S1220", null, inputFile.getPath()),
      tuple("squid:UnusedPrivateMethod", 8, inputFile.getPath()),
      tuple("squid:S1186", 8, inputFile.getPath()));
  }

  @Test
  public void simpleJavaWithExcludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final Collection<RuleKey> excludedRules = singleton(new RuleKey("squid", "S106"));
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addExcludedRules(excludedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simpleJavaWithIncludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}",
      false);

    final Collection<RuleKey> includedRules = singleton(new RuleKey("squid", "S3553"));
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRules(includedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S3553", 3, inputFile.getPath(), "MAJOR"),
      tuple("squid:S106", 5, inputFile.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 4, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simpleJavaWithIssueOnDir() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo/Foo.java",
      "package foo;\n"
        + "public class Foo {\n"
        + "}",
      false);

    final Collection<RuleKey> includedRules = singleton(new RuleKey("squid", "S1228"));
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addIncludedRules(includedRules)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S2094", 2, inputFile.getPath(), "MINOR"),
      tuple("squid:S1228", null, null, "MINOR"));
  }

  @Test
  public void simpleJavaWithIncludedAndExcludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
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

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 5, inputFile.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 4, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void testJavaSurefireDontCrashAnalysis() throws Exception {

    File surefireReport = new File(baseDir, "reports/TEST-FooTest.xml");
    FileUtils.write(surefireReport, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<testsuite name=\"FooTest\" time=\"0.121\" tests=\"1\" errors=\"0\" skipped=\"0\" failures=\"0\">\n" +
      "<testcase name=\"errorAnalysis\" classname=\"FooTest\" time=\"0.031\"/>\n" +
      "</testsuite>", StandardCharsets.UTF_8);

    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    ClientInputFile inputFileTest = prepareInputFile("FooTest.java",
      "public class FooTest {\n"
        + "  public void testFoo() {\n"
        + "  }\n"
        + "}",
      true);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile, inputFileTest)
        .putExtraProperty("sonar.junit.reportsPath", "reports/")
        .build(),
      issues::add, null, null);

    assertThat(results.indexedFileCount()).isEqualTo(2);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("squid:S106", 4, inputFile.getPath()),
      tuple("squid:S1220", null, inputFile.getPath()),
      tuple("squid:S1481", 3, inputFile.getPath()),
      tuple("squid:S2187", 1, inputFileTest.getPath()));
  }

  @Test
  public void concurrentAnalysis() throws Throwable {
    final ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final Path workDir = temp.newFolder().toPath();

    int parallelExecutions = 4;

    ExecutorService executor = Executors.newFixedThreadPool(parallelExecutions);

    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < parallelExecutions; i++) {

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
  public void lazy_init_file_metadata() throws Exception {
    final ClientInputFile inputFile1 = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);
    File unexistingPath = new File(baseDir, "missing.bin");
    assertThat(unexistingPath).doesNotExist();
    ClientInputFile inputFile2 = new TestClientInputFile(unexistingPath.toPath(), "missing.bin", false, StandardCharsets.UTF_8, null);

    final List<Issue> issues = new ArrayList<>();
    final List<String> logs = new ArrayList<>();
    AnalysisResults analysisResults = sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile1, inputFile2)
        .build(),
      issues::add,
      (m, l) -> logs.add(m), null);

    assertThat(analysisResults.failedAnalysisFiles()).isEmpty();
    assertThat(analysisResults.indexedFileCount()).isEqualTo(2);
    assertThat(logs).contains("Initializing metadata of file " + inputFile1.uri());
    assertThat(logs).doesNotContain("Initializing metadata of file " + inputFile2.uri());
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding, @Nullable String language) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    return new TestClientInputFile(file.toPath(), relativePath, isTest, encoding, language);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8, null);
  }

}
