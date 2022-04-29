/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Rules;
import org.sonarqube.ws.Rules.Rule;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.mediumtest.ConnectedIssueMediumTests.StoreIssueListener;
import org.sonarsource.sonarlint.core.mediumtest.fixtures.ProjectStorageFixture;
import testutils.PluginLocator;
import testutils.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension.httpClient;
import static org.sonarsource.sonarlint.core.mediumtest.fixtures.StorageFixture.newStorage;

class ConnectedEmbeddedPluginMediumTests {

  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  private static final String SERVER_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static ConnectedSonarLintEngineImpl sonarlint;

  @BeforeAll
  static void prepare(@TempDir Path slHome) throws Exception {
    var storage = newStorage(SERVER_ID)
      .withJSPlugin()
      .withJavaPlugin()
      .withProject("test-project")
      .withProject("stale_module", ProjectStorageFixture.ProjectStorageBuilder::stale)
      .withProject(JAVA_MODULE_KEY, project -> project
        .withRuleSet("java", ruleSet -> ruleSet
          // Emulate server returning a deprecated key for local analyzer
          .withActiveRule("squid:S106", "BLOCKER")
          // Emulate server returning a deprecated template key
          .withCustomActiveRule("squid:myCustomRule", "squid:S124", "MAJOR", Map.of("message", "Needs to be reviewed", "regularExpression", ".*REVIEW.*"))
          .withActiveRule("java:S1220", "MINOR")
          .withActiveRule("java:S1481", "BLOCKER")))
      .create(slHome);

    var nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    var config = ConnectedGlobalConfiguration.builder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput((m, l) -> System.out.println(m))
      .addEnabledLanguages(Language.JAVA, Language.JS)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .useEmbeddedPlugin("java", PluginLocator.getJavaPluginPath())
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
        + "<p>If a program directly writes to the standard outputs, there is absolutely no way to comply with those requirements. That's why defining and using a\n"
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
        + "  <li> <a href=\"https://www.securecoding.cert.org/confluence/x/RoElAQ\">CERT, ERR02-J.</a> - Prevent exceptions while logging data </li>\n"
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

    // Reported issues will refers to new rule keys
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("java:S106", 4, inputFile.getPath(), "BLOCKER"),
      tuple("java:myCustomRule", 5, inputFile.getPath(), "MAJOR"),
      tuple("java:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("java:S1481", 3, inputFile.getPath(), "BLOCKER"));

    // Requests to the server should be made using deprecated rule keys
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=squid:S106",
      Rules.ShowResponse.newBuilder().setRule(Rule.newBuilder().setLang(Language.JAVA.getLanguageKey()).setHtmlNote("S106 Extended rule description")).build());
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=squid:myCustomRule",
      Rules.ShowResponse.newBuilder().setRule(Rule.newBuilder().setLang(Language.JAVA.getLanguageKey()).setHtmlDesc("My custom rule template desc").setHtmlNote("My custom rule extended description")).build());

    ConnectedRuleDetails s106RuleDetails = sonarlint.getActiveRuleDetails(mockWebServerExtension.endpointParams(), httpClient(), "java:S106", JAVA_MODULE_KEY).get();
    assertThat(s106RuleDetails.getSeverity()).isEqualTo("BLOCKER");
    assertThat(s106RuleDetails.getLanguage()).isEqualTo(Language.JAVA);
    assertThat(s106RuleDetails.getHtmlDescription()).contains("<p>When logging a message there are several important requirements");
    assertThat(s106RuleDetails.getExtendedDescription()).isEqualTo("S106 Extended rule description");

    ConnectedRuleDetails myCustomRuleDetails = sonarlint.getActiveRuleDetails(mockWebServerExtension.endpointParams(), httpClient(), "java:myCustomRule", JAVA_MODULE_KEY).get();
    assertThat(myCustomRuleDetails.getSeverity()).isEqualTo("MAJOR");
    assertThat(s106RuleDetails.getLanguage()).isEqualTo(Language.JAVA);
    assertThat(myCustomRuleDetails.getHtmlDescription()).isEqualTo("My custom rule template desc");
    assertThat(myCustomRuleDetails.getExtendedDescription()).isEqualTo("My custom rule extended description");
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
