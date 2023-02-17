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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mediumtest.fixtures.StorageFixture;
import mediumtest.fixtures.TestPlugin;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleNonContextualSectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetActiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import testutils.MockWebServerExtensionWithProtobuf;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ActiveRulesMediumTests {

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_return_embedded_rule_when_project_is_not_bound() {
    backend = newBackend()
      .withUnboundConfigScope("scopeId")
      .withStorageRoot(storageDir)
      .withStandaloneEmbeddedPlugin(TestPlugin.PYTHON)
      .build();

    var details = getActiveRuleDetails("scopeId", "python:S139");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description.left.htmlContent")
      .containsExactly("python:S139", "Comments should not be located at the end of lines of code", RuleType.CODE_SMELL, Language.PYTHON, IssueSeverity.MINOR,
        PYTHON_S139_DESCRIPTION);
    assertThat(details.getParams())
      .extracting("name", "description", "defaultValue")
      .containsExactly(tuple("legalTrailingCommentPattern", null, "^#\\s*+[^\\s]++$"));
  }

  @Test
  void it_should_fail_when_rule_key_unknown_and_project_is_not_bound() {
    backend = newBackend()
      .withUnboundConfigScope("scopeId")
      .withStorageRoot(storageDir)
      .withStandaloneEmbeddedPlugin(TestPlugin.PYTHON)
      .build();

    var futureResponse = backend.getActiveRulesService().getActiveRuleDetails(new GetActiveRuleDetailsParams("scopeId", "python:SXXXX"));

    assertThat(futureResponse).failsWithin(1, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(IllegalArgumentException.class)
      .withMessageContaining("Could not find rule 'python:SXXXX' in embedded rules");
  }

  @Test
  void it_should_return_rule_loaded_from_server_plugin_when_project_is_bound_and_project_storage_does_not_exist() {
    StorageFixture.newStorage("connectionId")
      .withJavaPlugin()
      .create(storageDir);
    backend = newBackend()
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .withEnabledLanguage(Language.JAVA)
      .build();

    var details = getActiveRuleDetails("scopeId", "java:S106");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description.left.htmlContent")
      .containsExactly("java:S106", "Standard outputs should not be used directly to log anything", RuleType.CODE_SMELL, Language.JAVA, IssueSeverity.MAJOR,
        JAVA_S106_DESCRIPTION);
    assertThat(details.getParams()).isEmpty();
  }

  @Test
  void it_should_merge_rule_from_storage_and_server_when_project_is_bound() {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(storageDir);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .withConnectedEmbeddedPlugin(TestPlugin.PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc").build())
      .build());

    var details = getActiveRuleDetails("scopeId", "python:S139");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description.left.htmlContent")
      .containsExactly("python:S139", "Comments should not be located at the end of lines of code", RuleType.CODE_SMELL, Language.PYTHON, IssueSeverity.INFO,
        PYTHON_S139_DESCRIPTION + "<br/><br/>extendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @Test
  void it_should_merge_rule_from_storage_and_server_when_parent_project_is_bound() {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(storageDir);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withChildConfigScope("childScopeId", "scopeId")
      .withStorageRoot(storageDir.resolve("storage"))
      .withConnectedEmbeddedPlugin(TestPlugin.PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc").build())
      .build());

    var details = getActiveRuleDetails("childScopeId", "python:S139");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description.left.htmlContent")
      .containsExactly("python:S139", "Comments should not be located at the end of lines of code", RuleType.CODE_SMELL, Language.PYTHON, IssueSeverity.INFO,
        PYTHON_S139_DESCRIPTION + "<br/><br/>extendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @Test
  void it_return_single_section_from_server_when_project_is_bound() {
    var name = "name";
    var desc = "desc";
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.JS.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("jssecurity:S5696", "BLOCKER")))
      .create(storageDir);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=jssecurity:S5696", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName(name).setSeverity("BLOCKER").setType(Common.RuleType.VULNERABILITY).setLang("js")
        .setHtmlDesc(desc)
        .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("default")
            .setContent(desc)
            .build())
          .build())
        .build())
      .build());

    var details = getActiveRuleDetails("scopeId", "jssecurity:S5696");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description.left.htmlContent")
      .containsExactly("jssecurity:S5696", name, RuleType.VULNERABILITY, Language.JS, IssueSeverity.BLOCKER, desc);
    assertThat(details.getParams()).isEmpty();
  }

  @Test
  void it_should_fail_to_merge_rule_from_storage_and_server_when_connection_is_unknown() {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(storageDir);
    backend = newBackend()
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc").build())
      .build());

    var futureResponse = backend.getActiveRulesService().getActiveRuleDetails(new GetActiveRuleDetailsParams("scopeId", "python:S139"));

    assertThat(futureResponse).failsWithin(1, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(IllegalStateException.class)
      .withMessageContaining("Unknown connection 'connectionId'");
  }

  @Test
  void it_should_fail_to_merge_rule_from_storage_and_server_when_rule_does_not_exist_on_server() {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(storageDir);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .build();

    var futureResponse = backend.getActiveRulesService().getActiveRuleDetails(new GetActiveRuleDetailsParams("scopeId", "python:S139"));

    assertThat(futureResponse).failsWithin(3, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(IllegalStateException.class)
      .withMessageContaining("Could not find rule 'python:S139' on 'connectionId'");
  }

  @Test
  void it_should_merge_template_rule_from_storage_and_server_when_project_is_bound() {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withCustomActiveRule("python:custom", "python:CommentRegularExpression", "INFO", Map.of("message", "msg", "regularExpression", "regExp"))))
      .create(storageDir);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .withConnectedEmbeddedPlugin(TestPlugin.PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:custom", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc").build())
      .build());

    var details = getActiveRuleDetails("scopeId", "python:custom");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description.left.htmlContent")
      .containsExactly("python:custom", "newName", RuleType.CODE_SMELL, Language.PYTHON, IssueSeverity.INFO, "desc<br/><br/>extendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @Test
  void it_should_merge_rule_from_storage_and_server_rule_when_rule_is_unknown_in_loaded_plugins() {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(storageDir);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .withEnabledLanguage(Language.PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc").build())
      .build());

    var details = getActiveRuleDetails("scopeId", "python:S139");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description.left.htmlContent")
      .containsExactly("python:S139", "newName", RuleType.BUG, Language.PYTHON, IssueSeverity.INFO, "desc<br/><br/>extendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @Test
  void it_should_merge_rule_from_storage_and_server_with_description_sections_when_project_is_bound_and_none_context() {
    prepareForRuleDescriptionSectionsAndContext();

    var details = getActiveRuleDetails("scopeId", "python:S139");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity")
      .containsExactly("python:S139", "newName", RuleType.BUG, Language.PYTHON, IssueSeverity.INFO);
    assertThat(details.getParams()).isEmpty();
    assertThat(details.getDescription().getRight().getIntroductionHtmlContent())
      .isEqualTo("htmlContent");
    assertThat(details.getDescription().getRight().getTabs())
      .flatExtracting(ActiveRulesMediumTests::flattenTabContent)
      .containsExactly(
        "How can I fix it?", "htmlContent2", "contextKey2", "displayName2",
        "How can I fix it?",
        "<h4>How can I fix it in another component or framework?</h4>\n"+
          "<p>Although the main framework or component you use in your project is not listed, you may find helpful content in the instructions we provide.</p>\n"+
          "<p>Caution: The libraries mentioned in these instructions may not be appropriate for your code.</p>\n"+
          "<p>\n"+
          "<ul>\n"+
          "    <li>Do use libraries that are compatible with the frameworks you are using.</li>\n"+
          "    <li>Don't blindly copy and paste the fix-ups into your code.</li>\n"+
          "</ul>\n"+
          "<h4>Help us improve</h4>\n"+
          "<p>Let us know if the instructions we provide do not work for you.\n"+
          "    Tell us which framework you use and why our solution does not work by submitting an idea on the SonarLint product-board.</p>\n"+
          "<a href=\"https://portal.productboard.com/sonarsource/4-sonarlint/submit-idea\">Submit an idea</a>\n"+
          "<p>We will do our best to provide you with more relevant instructions in the future.</p>", "others", "Others",
        "More Info", "htmlContent3<br/><br/>extendedDesc<br/><br/><h3>Clean Code Principles</h3>\n" +
          "<h4>Never Trust User Input</h4>\n" +
          "<p>\n" +
          "    Applications must treat all user input and, more generally, all third-party data as\n" +
          "    attacker-controlled data.\n" +
          "</p>\n" +
          "<p>\n" +
          "    The application must determine where the third-party data comes from and treat that data\n" +
          "    source as an attack vector. Two rules apply:\n" +
          "</p>\n" +
          "\n" +
          "<p>\n" +
          "    First, before using it in the application&apos;s business logic, the application must\n" +
          "    validate the attacker-controlled data against predefined formats, such as:\n" +
          "</p>\n" +
          "<ul>\n" +
          "    <li>Character sets</li>\n" +
          "    <li>Sizes</li>\n" +
          "    <li>Types</li>\n" +
          "    <li>Or any strict schema</li>\n" +
          "</ul>\n" +
          "\n" +
          "<p>\n" +
          "    Second, the application must sanitize string data before inserting it into interpreted\n" +
          "    contexts (client-side code, file paths, SQL queries). Unsanitized code can corrupt the\n" +
          "    application&apos;s logic.\n" +
          "</p>");

  }

  @Test
  void it_should_ignore_provided_context_and_return_all_contexts_in_alphabetical_order_with_default_if_context_not_found() {
    prepareForRuleDescriptionSectionsAndContext();

    var details = getActiveRuleDetails("scopeId", "python:S139", "not_found");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity")
      .containsExactly("python:S139", "newName", RuleType.BUG, Language.PYTHON, IssueSeverity.INFO);
    assertThat(details.getParams()).isEmpty();
    assertThat(details.getDescription().getRight().getIntroductionHtmlContent())
      .isEqualTo("htmlContent");
    assertThat(details.getDescription().getRight().getTabs())
      .flatExtracting(ActiveRulesMediumTests::flattenTabContent)
      .containsExactly(
        "How can I fix it?", "htmlContent2", "contextKey2", "displayName2",
        "How can I fix it?", "<h4>How can I fix it in another component or framework?</h4>\n"+
          "<p>Although the main framework or component you use in your project is not listed, you may find helpful content in the instructions we provide.</p>\n"+
          "<p>Caution: The libraries mentioned in these instructions may not be appropriate for your code.</p>\n"+
          "<p>\n"+
          "<ul>\n"+
          "    <li>Do use libraries that are compatible with the frameworks you are using.</li>\n"+
          "    <li>Don't blindly copy and paste the fix-ups into your code.</li>\n"+
          "</ul>\n"+
          "<h4>Help us improve</h4>\n"+
          "<p>Let us know if the instructions we provide do not work for you.\n"+
          "    Tell us which framework you use and why our solution does not work by submitting an idea on the SonarLint product-board.</p>\n"+
          "<a href=\"https://portal.productboard.com/sonarsource/4-sonarlint/submit-idea\">Submit an idea</a>\n"+
          "<p>We will do our best to provide you with more relevant instructions in the future.</p>", "others", "Others",
        "More Info", "htmlContent3<br/><br/>extendedDesc<br/><br/><h3>Clean Code Principles</h3>\n" +
          "<h4>Never Trust User Input</h4>\n" +
          "<p>\n" +
          "    Applications must treat all user input and, more generally, all third-party data as\n" +
          "    attacker-controlled data.\n" +
          "</p>\n" +
          "<p>\n" +
          "    The application must determine where the third-party data comes from and treat that data\n" +
          "    source as an attack vector. Two rules apply:\n" +
          "</p>\n" +
          "\n" +
          "<p>\n" +
          "    First, before using it in the application&apos;s business logic, the application must\n" +
          "    validate the attacker-controlled data against predefined formats, such as:\n" +
          "</p>\n" +
          "<ul>\n" +
          "    <li>Character sets</li>\n" +
          "    <li>Sizes</li>\n" +
          "    <li>Types</li>\n" +
          "    <li>Or any strict schema</li>\n" +
          "</ul>\n" +
          "\n" +
          "<p>\n" +
          "    Second, the application must sanitize string data before inserting it into interpreted\n" +
          "    contexts (client-side code, file paths, SQL queries). Unsanitized code can corrupt the\n" +
          "    application&apos;s logic.\n" +
          "</p>");

  }

  @Test
  void it_should_return_default_context_key_if_multiple_contexts() {
    prepareForRuleDescriptionSectionsAndContext();

    var details = getActiveRuleDetails("scopeId", "python:S139", "not_found");

    assertThat(details.getDescription().getRight().getTabs())
      .extracting(ActiveRuleDescriptionTabDto::getTitle).containsExactly("How can I fix it?", "More Info");

    assertThat(details.getDescription().getRight().getTabs().iterator().next().getContent().getRight().getDefaultContextKey())
      .isEqualTo("others");

  }

  private void prepareForRuleDescriptionSectionsAndContext() {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(storageDir);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .withEnabledLanguage(Language.PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc")
        .setEducationPrinciples(Rules.Rule.EducationPrinciples.newBuilder().addEducationPrinciples("never_trust_user_input").build())
        .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("introduction").setContent("htmlContent")
            .setContext(Rules.Rule.DescriptionSection.Context.newBuilder().setKey("contextKey").setDisplayName("displayName").build()).build())
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("how_to_fix").setContent("htmlContent2")
            .setContext(Rules.Rule.DescriptionSection.Context.newBuilder().setKey("contextKey2").setDisplayName("displayName2").build()).build())
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("resources").setContent("htmlContent3").build()))
        .build())
      .build());
  }

  @Test
  void it_should_return_only_tab_content_for_the_provided_context() {
    prepareForRuleDescriptionSectionsAndContext();

    var details = getActiveRuleDetails("scopeId", "python:S139", "contextKey2");

    assertThat(details)
      .extracting("key", "name", "type", "language", "severity")
      .containsExactly("python:S139", "newName", RuleType.BUG, Language.PYTHON, IssueSeverity.INFO);
    assertThat(details.getParams()).isEmpty();
    assertThat(details.getDescription())
      .extracting("right.introductionHtmlContent")
      .isEqualTo("htmlContent");
    assertThat(details.getDescription().getRight().getTabs())
      .flatExtracting(ActiveRulesMediumTests::flattenTabContent)
      .containsExactly(
        "How can I fix it?", "htmlContent2",
        "More Info", "htmlContent3<br/><br/>extendedDesc<br/><br/><h3>Clean Code Principles</h3>\n" +
          "<h4>Never Trust User Input</h4>\n" +
          "<p>\n" +
          "    Applications must treat all user input and, more generally, all third-party data as\n" +
          "    attacker-controlled data.\n" +
          "</p>\n" +
          "<p>\n" +
          "    The application must determine where the third-party data comes from and treat that data\n" +
          "    source as an attack vector. Two rules apply:\n" +
          "</p>\n" +
          "\n" +
          "<p>\n" +
          "    First, before using it in the application&apos;s business logic, the application must\n" +
          "    validate the attacker-controlled data against predefined formats, such as:\n" +
          "</p>\n" +
          "<ul>\n" +
          "    <li>Character sets</li>\n" +
          "    <li>Sizes</li>\n" +
          "    <li>Types</li>\n" +
          "    <li>Or any strict schema</li>\n" +
          "</ul>\n" +
          "\n" +
          "<p>\n" +
          "    Second, the application must sanitize string data before inserting it into interpreted\n" +
          "    contexts (client-side code, file paths, SQL queries). Unsanitized code can corrupt the\n" +
          "    application&apos;s logic.\n" +
          "</p>");
  }

  @Test
  void it_should_add_a_more_info_tab_if_no_resource_section_exists_and_extended_description_exists() {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(storageDir);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .withEnabledLanguage(Language.PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc")
        .setEducationPrinciples(Rules.Rule.EducationPrinciples.newBuilder().addEducationPrinciples("never_trust_user_input").build())
        .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("introduction").setContent("htmlContent")
            .setContext(Rules.Rule.DescriptionSection.Context.newBuilder().setKey("contextKey").setDisplayName("displayName").build()).build())
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("how_to_fix").setContent("htmlContent2")
            .setContext(Rules.Rule.DescriptionSection.Context.newBuilder().setKey("contextKey2").setDisplayName("displayName2").build()).build())
          .build())
        .build())
      .build());

    var details = getActiveRuleDetails("scopeId", "python:S139");

    assertThat(details.getDescription().getRight().getTabs())
      .filteredOn(ActiveRuleDescriptionTabDto::getTitle, "More Info")
      .extracting(ActiveRuleDescriptionTabDto::getContent)
      .extracting(Either::getLeft)
      .extracting(ActiveRuleNonContextualSectionDto::getHtmlContent)
      .containsExactly("extendedDesc<br/><br/><h3>Clean Code Principles</h3>\n" +
        "<h4>Never Trust User Input</h4>\n" +
        "<p>\n" +
        "    Applications must treat all user input and, more generally, all third-party data as\n" +
        "    attacker-controlled data.\n" +
        "</p>\n" +
        "<p>\n" +
        "    The application must determine where the third-party data comes from and treat that data\n" +
        "    source as an attack vector. Two rules apply:\n" +
        "</p>\n" +
        "\n" +
        "<p>\n" +
        "    First, before using it in the application&apos;s business logic, the application must\n" +
        "    validate the attacker-controlled data against predefined formats, such as:\n" +
        "</p>\n" +
        "<ul>\n" +
        "    <li>Character sets</li>\n" +
        "    <li>Sizes</li>\n" +
        "    <li>Types</li>\n" +
        "    <li>Or any strict schema</li>\n" +
        "</ul>\n" +
        "\n" +
        "<p>\n" +
        "    Second, the application must sanitize string data before inserting it into interpreted\n" +
        "    contexts (client-side code, file paths, SQL queries). Unsanitized code can corrupt the\n" +
        "    application&apos;s logic.\n" +
        "</p>");
  }

  @Test
  void it_should_split_security_hotspots_rule_description_and_adapt_title() {
    backend = newBackend()
      .withSonarQubeConnection("connectionId", "url")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withStorageRoot(storageDir)
      .withConnectedEmbeddedPlugin(TestPlugin.PYTHON)
      .withSecurityHotspotsEnabled()
      .build();

    var details = getActiveRuleDetails("scopeId", "python:S4784");

    assertThat(details.getDescription().isRight()).isTrue();
    assertThat(details.getDescription().getRight().getTabs())
      .hasSize(3)
      .extracting(ActiveRuleDescriptionTabDto::getTitle)
      .contains("What's the risk?");
  }

  private static List<Object> flattenTabContent(ActiveRuleDescriptionTabDto tab) {
    if (tab.getContent().isLeft()) {
      return List.of(tab.getTitle(), tab.getContent().getLeft().getHtmlContent());
    }
    return tab.getContent().getRight().getContextualSections().stream().flatMap(s -> Stream.of(tab.getTitle(), s.getHtmlContent(), s.getContextKey(), s.getDisplayName())).collect(Collectors.toList());
  }

  private ActiveRuleDetailsDto getActiveRuleDetails(String configScopeId, String ruleKey) {
    return getActiveRuleDetails(configScopeId, ruleKey, null);
  }

  private ActiveRuleDetailsDto getActiveRuleDetails(String configScopeId, String ruleKey, String contextKey) {
    try {
      return this.backend.getActiveRulesService().getActiveRuleDetails(new GetActiveRuleDetailsParams(configScopeId, ruleKey, contextKey)).get().details();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @TempDir
  Path storageDir;
  private SonarLintBackendImpl backend;
  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();
  private static final String PYTHON_S139_DESCRIPTION = "<p>This rule verifies that single-line comments are not located at the ends of lines of code. The main idea behind this rule is that in order to be\n"
    +
    "really readable, trailing comments would have to be properly written and formatted (correct alignment, no interference with the visual structure of\n" +
    "the code, not too long to be visible) but most often, automatic code formatters would not handle this correctly: the code would end up less readable.\n" +
    "Comments are far better placed on the previous empty line of code, where they will always be visible and properly formatted.</p>\n" +
    "<h2>Noncompliant Code Example</h2>\n" +
    "<pre>\n" +
    "a = b + c   # This is a trailing comment that can be very very long\n" +
    "</pre>\n" +
    "<h2>Compliant Solution</h2>\n" +
    "<pre>\n" +
    "# This very long comment is better placed before the line of code\n" +
    "a = b + c\n" +
    "</pre>";
  private static final String JAVA_S106_DESCRIPTION = "<p>When logging a message there are several important requirements which must be fulfilled:</p>\n" +
    "<ul>\n" +
    "  <li> The user must be able to easily retrieve the logs </li>\n" +
    "  <li> The format of all logged message must be uniform to allow the user to easily read the log </li>\n" +
    "  <li> Logged data must actually be recorded </li>\n" +
    "  <li> Sensitive data must only be logged securely </li>\n" +
    "</ul>\n" +
    "<p>If a program directly writes to the standard outputs, there is absolutely no way to comply with those requirements. That’s why defining and using a\n" +
    "dedicated logger is highly recommended.</p>\n" +
    "<h2>Noncompliant Code Example</h2>\n" +
    "<pre>\n" +
    "System.out.println(\"My Message\");  // Noncompliant\n" +
    "</pre>\n" +
    "<h2>Compliant Solution</h2>\n" +
    "<pre>\n" +
    "logger.log(\"My Message\");\n" +
    "</pre>\n" +
    "<h2>See</h2>\n" +
    "<ul>\n" +
    "  <li> <a href=\"https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/\">OWASP Top 10 2021 Category A9</a> - Security Logging and\n" +
    "  Monitoring Failures </li>\n" +
    "  <li> <a href=\"https://www.owasp.org/www-project-top-ten/2017/A3_2017-Sensitive_Data_Exposure\">OWASP Top 10 2017 Category A3</a> - Sensitive Data\n" +
    "  Exposure </li>\n" +
    "  <li> <a href=\"https://wiki.sei.cmu.edu/confluence/x/nzdGBQ\">CERT, ERR02-J.</a> - Prevent exceptions while logging data </li>\n" +
    "</ul>";
}
