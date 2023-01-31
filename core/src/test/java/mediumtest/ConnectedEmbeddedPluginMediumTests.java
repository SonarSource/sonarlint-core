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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mediumtest.ConnectedIssueMediumTests.StoreIssueListener;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.RuleType;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules.Rule;
import testutils.MockWebServerExtensionWithProtobuf;
import testutils.PluginLocator;
import testutils.TestUtils;

import static mediumtest.fixtures.StorageFixture.newStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension.httpClient;

class ConnectedEmbeddedPluginMediumTests {

  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  private static final String SERVER_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static ConnectedSonarLintEngineImpl sonarlint;

  @BeforeAll
  static void prepare(@TempDir Path slHome) {
    var storage = newStorage(SERVER_ID)
      .withServerVersion("9.8")
      .withJSPlugin()
      .withJavaPlugin()
      .withProject("test-project")
      .withProject(JAVA_MODULE_KEY, project -> project
        .withRuleSet("java", ruleSet -> ruleSet
          // Emulate server returning a deprecated key for local analyzer
          .withActiveRule("squid:S106", "BLOCKER")
          .withActiveRule("java:S3776", "BLOCKER", Map.of("blah", "blah"))
          // Emulate server returning a deprecated template key
          .withCustomActiveRule("squid:myCustomRule", "squid:S124", "MAJOR", Map.of("message", "Needs to be reviewed", "regularExpression", ".*REVIEW.*"))
          .withActiveRule("java:S1220", "MINOR")
          .withActiveRule("java:S1481", "BLOCKER")))
      .create(slHome);

    var nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput((m, l) -> System.out.println(m))
      .addEnabledLanguages(Language.JAVA, Language.JS, Language.SECRETS)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .useEmbeddedPlugin("java", PluginLocator.getJavaPluginPath())
      .useEmbeddedPlugin("text", PluginLocator.getTextPluginPath())
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
  }

  @AfterAll
  static void stop() {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @Test
  void rule_description_come_from_embedded() throws Exception {
    assertThat(sonarlint.getActiveRuleDetails(null, null, "java:S106", null).get().getHtmlDescription())
      .isEqualTo("<p>When logging a message there are several important requirements which must be fulfilled:</p>\n"
        + "<ul>\n"
        + "  <li> The user must be able to easily retrieve the logs </li>\n"
        + "  <li> The format of all logged message must be uniform to allow the user to easily read the log </li>\n"
        + "  <li> Logged data must actually be recorded </li>\n"
        + "  <li> Sensitive data must only be logged securely </li>\n"
        + "</ul>\n"
        + "<p>If a program directly writes to the standard outputs, there is absolutely no way to comply with those requirements. That’s why defining and using a\n"
        + "dedicated logger is highly recommended.</p>\n"
        + "<h2>Noncompliant Code Example</h2>\n"
        + "<pre>\n"
        + "System.out.println(\"My Message\");  // Noncompliant\n"
        + "</pre>\n"
        + "<h2>Compliant Solution</h2>\n"
        + "<pre>\n"
        + "logger.log(\"My Message\");\n"
        + "</pre>\n"
        + "<h2>See</h2>\n"
        + "<ul>\n"
        + "  <li> <a href=\"https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/\">OWASP Top 10 2021 Category A9</a> - Security Logging and\n"
        + "  Monitoring Failures </li>\n"
        + "  <li> <a href=\"https://www.owasp.org/www-project-top-ten/2017/A3_2017-Sensitive_Data_Exposure\">OWASP Top 10 2017 Category A3</a> - Sensitive Data\n"
        + "  Exposure </li>\n"
        + "  <li> <a href=\"https://wiki.sei.cmu.edu/confluence/x/nzdGBQ\">CERT, ERR02-J.</a> - Prevent exceptions while logging data </li>\n"
        + "</ul>");
  }

  /**
   * SLCORE-365
   * The server is only aware of rules squid:S106 and squid:myCustomRule (that is a custom rule based on template rule squid:S124)
   * The embedded analyzer is only aware of rules java:S106 and java:S124
   */
  @Test
  void convert_deprecated_keys_from_server_for_rules_and_templates(@TempDir Path baseDir) throws Exception {
    var inputFile = prepareJavaInputFile(baseDir);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setProjectKey(JAVA_MODULE_KEY)
      .setBaseDir(baseDir)
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null);

    // Reported issues will refer to new rule keys
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("java:S106", 4, inputFile.getPath(), IssueSeverity.BLOCKER),
      tuple("java:myCustomRule", 5, inputFile.getPath(), IssueSeverity.MAJOR),
      tuple("java:S1220", null, inputFile.getPath(), IssueSeverity.MINOR),
      tuple("java:S1481", 3, inputFile.getPath(), IssueSeverity.BLOCKER));

    // Requests to the server should be made using deprecated rule keys
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=squid:S106",
      Rules.ShowResponse.newBuilder()
        .setRule(Rule.newBuilder().setLang(Language.JAVA.getLanguageKey()).setHtmlNote("S106 Extended rule description").setSeverity("MAJOR").setType(RuleType.BUG)).build());
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=squid:myCustomRule",
      Rules.ShowResponse.newBuilder()
        .setRule(Rule.newBuilder().setLang(Language.JAVA.getLanguageKey()).setHtmlDesc("My custom rule template desc").setHtmlNote("My custom rule extended description")
          .setSeverity("MINOR").setType(RuleType.CODE_SMELL))
        .build());

    ConnectedRuleDetails s106RuleDetails = sonarlint.getActiveRuleDetails(mockWebServerExtension.endpointParams(), httpClient(), "java:S106", JAVA_MODULE_KEY).get();
    assertThat(s106RuleDetails.getDefaultSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(s106RuleDetails.getLanguage()).isEqualTo(Language.JAVA);
    assertThat(s106RuleDetails.getType()).isEqualTo(org.sonarsource.sonarlint.core.commons.RuleType.CODE_SMELL);
    assertThat(s106RuleDetails.getHtmlDescription()).contains("<p>When logging a message there are several important requirements");
    assertThat(s106RuleDetails.getExtendedDescription()).isEqualTo("S106 Extended rule description");

    ConnectedRuleDetails myCustomRuleDetails = sonarlint.getActiveRuleDetails(mockWebServerExtension.endpointParams(), httpClient(), "java:myCustomRule", JAVA_MODULE_KEY).get();
    assertThat(myCustomRuleDetails.getDefaultSeverity()).isEqualTo(IssueSeverity.MAJOR);
    assertThat(s106RuleDetails.getLanguage()).isEqualTo(Language.JAVA);
    assertThat(myCustomRuleDetails.getHtmlDescription()).isEqualTo("My custom rule template desc");
    assertThat(myCustomRuleDetails.getExtendedDescription()).isEqualTo("My custom rule extended description");
  }

  @Test
  void ignore_unknown_active_rule_parameters(@TempDir Path baseDir) throws Exception {
    var inputFile = prepareInputFile(baseDir, "Foo.java", "package com;\n" +
      "public class Foo {\n"
      + "  public void foo() {\n"
      + "    while (true) {\n"
      + "      while (true) {\n"
      + "        while (true) {\n"
      + "          while (true) {\n"
      + "            while (true) {\n"
      + "              while (true) {\n"
      + "              }\n"
      + "            }\n"
      + "          }\n"
      + "        }\n"
      + "      }\n"
      + "    }\n"
      + "  }\n"
      + "}", false);
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setProjectKey(JAVA_MODULE_KEY)
      .setBaseDir(baseDir)
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("java:S3776", 3, inputFile.getPath(), IssueSeverity.BLOCKER));
  }

  @Test
  void secrets_rules_should_always_be_active_with_legacy_sonarqube(@TempDir Path baseDir) throws IOException {
    var inputFile = prepareInputFile(baseDir, "t.txt",
      "  public static final String KEY = \"AKIAIGKECZXA7EXAMPLF\"\n" , false);
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
        .setProjectKey(JAVA_MODULE_KEY)
        .setBaseDir(baseDir)
        .addInputFile(inputFile)
        .setModuleKey("key")
        .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("secrets:S6290", 1, inputFile.getPath(), IssueSeverity.BLOCKER));
  }

  private ClientInputFile prepareJavaInputFile(Path baseDir) throws IOException {
    return prepareInputFile(baseDir, "Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    // TO REVIEW\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareInputFile(Path baseDir, String relativePath, String content, final boolean isTest) throws IOException {
    final var file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }

}
