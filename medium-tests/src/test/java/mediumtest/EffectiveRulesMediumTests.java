/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleParamDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleNonContextualSectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.MockWebServerExtensionWithProtobuf;
import utils.TestPlugin;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute.CONVENTIONAL;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity.LOW;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity.MEDIUM;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.BLOCKER;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.INFO;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PYTHON;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.BUG;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.VULNERABILITY;

class EffectiveRulesMediumTests {

  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  @SonarLintTest
  void it_should_return_embedded_rule_when_project_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope("scopeId")
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build();

    var details = getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getCleanCodeAttribute, EffectiveRuleDetailsDto::getLanguage,
        r -> r.getDefaultImpacts().get(0).getImpactSeverity(), r -> r.getDescription().getLeft().getHtmlContent())
      .containsExactly("python:S139", "Comments should not be located at the end of lines of code", CONVENTIONAL, PYTHON, LOW,
        PYTHON_S139_DESCRIPTION);
    assertThat(details.getParams())
      .extracting(EffectiveRuleParamDto::getName, EffectiveRuleParamDto::getDescription, EffectiveRuleParamDto::getValue, EffectiveRuleParamDto::getDefaultValue)
      .containsExactly(tuple("legalTrailingCommentPattern",
        "Pattern for text of trailing comments that are allowed. By default, Mypy and Black pragma comments as well as comments containing only one word.",
        "^#\\s*+([^\\s]++|fmt.*|type.*)$",
        "^#\\s*+([^\\s]++|fmt.*|type.*)$"));
  }

  @SonarLintTest
  void it_should_consider_standalone_rule_config_for_effective_parameter_values(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope("scopeId")
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withStandaloneRuleConfig("python:S139", true, Map.of("legalTrailingCommentPattern", "initialValue"))
      .build();

    var detailsAfterInit = getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    assertThat(detailsAfterInit.getParams())
      .extracting(EffectiveRuleParamDto::getName, EffectiveRuleParamDto::getDescription, EffectiveRuleParamDto::getValue, EffectiveRuleParamDto::getDefaultValue)
      .containsExactly(tuple("legalTrailingCommentPattern",
        "Pattern for text of trailing comments that are allowed. By default, Mypy and Black pragma comments as well as comments containing only one word.",
        "initialValue",
        "^#\\s*+([^\\s]++|fmt.*|type.*)$"));

    backend.getRulesService().updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("python:S139",
      new StandaloneRuleConfigDto(true, Map.of("legalTrailingCommentPattern", "updatedValue")))));

    var detailsAfterUpdate = getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    assertThat(detailsAfterUpdate.getParams())
      .extracting(EffectiveRuleParamDto::getName, EffectiveRuleParamDto::getDescription, EffectiveRuleParamDto::getValue, EffectiveRuleParamDto::getDefaultValue)
      .containsExactly(tuple("legalTrailingCommentPattern",
        "Pattern for text of trailing comments that are allowed. By default, Mypy and Black pragma comments as well as comments containing only one word.",
        "updatedValue",
        "^#\\s*+([^\\s]++|fmt.*|type.*)$"));
  }

  @SonarLintTest
  void it_should_fail_when_rule_key_unknown_and_project_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope("scopeId")
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build();

    var futureResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("scopeId", "python:SXXXX"));

    assertThat(futureResponse).failsWithin(1, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Could not find rule 'python:SXXXX' in embedded rules");
  }

  @SonarLintTest
  void it_should_return_rule_loaded_from_server_plugin_when_project_is_bound_and_project_storage_does_not_exist(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withPlugin(TestPlugin.JAVA))
      .withEnabledLanguageInStandaloneMode(JAVA)
      .build();

    var details = getEffectiveRuleDetails(backend, "scopeId", "java:S106");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getCleanCodeAttribute, EffectiveRuleDetailsDto::getLanguage,
        r -> r.getDefaultImpacts().get(0).getImpactSeverity(), r -> r.getDescription().getLeft().getHtmlContent())
      .containsExactly("java:S106", "Standard outputs should not be used directly to log anything", CONVENTIONAL, JAVA, MEDIUM,
        JAVA_S106_DESCRIPTION);
    assertThat(details.getParams()).isEmpty();
  }

  @SonarLintTest
  void it_should_merge_rule_from_storage_and_server_when_project_is_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet("python",
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlNote("extendedDesc").build())
      .build());

    var details = getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getCleanCodeAttribute, EffectiveRuleDetailsDto::getLanguage,
        r -> r.getDefaultImpacts().get(0).getImpactSeverity(), r -> r.getDescription().getLeft().getHtmlContent())
      .containsExactly("python:S139", "Comments should not be located at the end of lines of code", CONVENTIONAL, PYTHON, LOW,
        PYTHON_S139_DESCRIPTION + "extendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @SonarLintTest
  void it_should_merge_rule_from_storage_and_server_when_parent_project_is_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet("python",
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withChildConfigScope("childScopeId", "scopeId")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlNote("extendedDesc").build())
      .build());

    var details = getEffectiveRuleDetails(backend, "childScopeId", "python:S139");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getCleanCodeAttribute, EffectiveRuleDetailsDto::getLanguage,
        r -> r.getDefaultImpacts().get(0).getImpactSeverity(), r -> r.getDescription().getLeft().getHtmlContent())
      .containsExactly("python:S139", "Comments should not be located at the end of lines of code", CONVENTIONAL, PYTHON, LOW,
        PYTHON_S139_DESCRIPTION + "extendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @SonarLintTest
  void it_return_single_section_from_server_when_project_is_bound(SonarLintTestHarness harness) {
    var name = "name";
    var desc = "desc";
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet("js",
          ruleSet -> ruleSet.withActiveRule("jssecurity:S5696", "BLOCKER"))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=jssecurity:S5696", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName(name).setSeverity("BLOCKER").setType(Common.RuleType.VULNERABILITY).setLang("js")
        .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("default")
            .setContent(desc)
            .build())
          .build())
        .build())
      .build());

    var details = getEffectiveRuleDetails(backend, "scopeId", "jssecurity:S5696");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getType, EffectiveRuleDetailsDto::getLanguage,
        EffectiveRuleDetailsDto::getSeverity, r -> r.getDescription().getLeft().getHtmlContent())
      .containsExactly("jssecurity:S5696", name, VULNERABILITY, Language.JS, BLOCKER, desc);
    assertThat(details.getParams()).isEmpty();
  }

  @SonarLintTest
  void it_should_fail_to_merge_rule_from_storage_and_server_when_connection_is_unknown(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withStorage("connectionId", storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet("python",
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build();

    var futureResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("scopeId", "python:S139"));

    assertThat(futureResponse).failsWithin(1, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Connection with ID 'connectionId' does not exist");
  }

  @SonarLintTest
  void it_should_fail_to_merge_rule_from_storage_and_server_when_rule_does_not_exist_on_server(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet("python",
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build();

    var futureResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("scopeId", "python:S139"));

    assertThat(futureResponse).failsWithin(3, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getMessage()).contains("Could not find rule 'python:S139' on server 'connectionId'");
      });
  }

  @SonarLintTest
  void it_should_merge_template_rule_from_storage_and_server_when_project_is_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet("python",
          ruleSet -> ruleSet.withCustomActiveRule("python:custom", "python:CommentRegularExpression", "INFO", Map.of("message", "msg", "regularExpression", "regExp")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:custom", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlNote("extendedDesc")
        .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("default")
            .setContent("desc")
            .build())
          .build())
        .build())
      .build());

    var details = getEffectiveRuleDetails(backend, "scopeId", "python:custom");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getCleanCodeAttribute, EffectiveRuleDetailsDto::getLanguage,
        r -> r.getDefaultImpacts().get(0).getImpactSeverity(), r -> r.getDescription().getLeft().getHtmlContent())
      .containsExactly("python:custom", "newName", CONVENTIONAL, PYTHON, MEDIUM, "descextendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @SonarLintTest
  void it_should_merge_rule_from_storage_and_server_rule_when_rule_is_unknown_in_loaded_plugins(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet("python",
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withEnabledLanguageInStandaloneMode(PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlNote("extendedDesc")
        .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("default")
            .setContent("desc")
            .build())
          .build())
        .build())
      .build());

    var details = getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getType, EffectiveRuleDetailsDto::getLanguage,
        EffectiveRuleDetailsDto::getSeverity, r -> r.getDescription().getLeft().getHtmlContent())
      .containsExactly("python:S139", "newName", BUG, PYTHON, INFO, "descextendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @SonarLintTest
  void it_should_merge_rule_from_storage_and_server_with_description_sections_when_project_is_bound_and_none_context(SonarLintTestHarness harness) {
    var backend = prepareForRuleDescriptionSectionsAndContext(harness);

    var details = getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getType, EffectiveRuleDetailsDto::getLanguage,
        EffectiveRuleDetailsDto::getSeverity)
      .containsExactly("python:S139", "newName", BUG, PYTHON, INFO);
    assertThat(details.getParams()).isEmpty();
    assertThat(details.getDescription().getRight().getIntroductionHtmlContent())
      .isEqualTo("intro content");
    assertThat(details.getDescription().getRight().getTabs())
      .flatExtracting(EffectiveRulesMediumTests::flattenTabContent)
      .containsExactly(
        "How can I fix it?",
        "--> Spring (spring)",
        "    fix spring",
        "--> Struts (struts)",
        "    fix struts",
        "--> Others (others)",
        "    <h4>How can I fix it in another component or fr...",
        "More Info",
        "htmlContent3extendedDesc<h3>Clean Code Principl...");
  }

  @SonarLintTest
  void it_should_return_all_contexts_in_alphabetical_order_with_others_as_default_if_context_not_found(SonarLintTestHarness harness) {
    var backend = prepareForRuleDescriptionSectionsAndContext(harness);

    var details = getEffectiveRuleDetails(backend, "scopeId", "python:S139", "not_found");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getType, EffectiveRuleDetailsDto::getLanguage,
        EffectiveRuleDetailsDto::getSeverity)
      .containsExactly("python:S139", "newName", BUG, PYTHON, INFO);
    assertThat(details.getParams()).isEmpty();
    assertThat(details.getDescription().getRight().getIntroductionHtmlContent())
      .isEqualTo("intro content");
    assertThat(details.getDescription().getRight().getTabs())
      .flatExtracting(EffectiveRulesMediumTests::flattenTabContent)
      .containsExactly(
        "How can I fix it?",
        "--> Spring (spring)",
        "    fix spring",
        "--> Struts (struts)",
        "    fix struts",
        "--> Others (others)",
        "    <h4>How can I fix it in another component or fr...",
        "More Info",
        "htmlContent3extendedDesc<h3>Clean Code Principl...");

    assertThat(details.getDescription().getRight().getTabs().iterator().next().getContent().getRight().getDefaultContextKey())
      .isEqualTo("others");
  }

  @SonarLintTest
  void it_should_return_all_contexts_in_alphabetical_order_with_the_provided_context_as_default(SonarLintTestHarness harness) {
    var backend = prepareForRuleDescriptionSectionsAndContext(harness);

    var details = getEffectiveRuleDetails(backend, "scopeId", "python:S139", "spring");

    assertThat(details)
      .extracting(EffectiveRuleDetailsDto::getKey, EffectiveRuleDetailsDto::getName, EffectiveRuleDetailsDto::getType, EffectiveRuleDetailsDto::getLanguage,
        EffectiveRuleDetailsDto::getSeverity)
      .containsExactly("python:S139", "newName", BUG, PYTHON, INFO);
    assertThat(details.getParams()).isEmpty();
    assertThat(details.getDescription())
      .extracting("right.introductionHtmlContent")
      .isEqualTo("intro content");
    assertThat(details.getDescription().getRight().getTabs())
      .flatExtracting(EffectiveRulesMediumTests::flattenTabContent)
      .containsExactly(
        "How can I fix it?",
        "--> Spring (spring)",
        "    fix spring",
        "--> Struts (struts)",
        "    fix struts",
        "--> Others (others)",
        "    <h4>How can I fix it in another component or fr...",
        "More Info",
        "htmlContent3extendedDesc<h3>Clean Code Principl...");

    assertThat(details.getDescription().getRight().getTabs().iterator().next().getContent().getRight().getDefaultContextKey())
      .isEqualTo("spring");
  }

  @SonarLintTest
  void it_should_add_a_more_info_tab_if_no_resource_section_exists_and_extended_description_exists(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet("python",
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withEnabledLanguageInStandaloneMode(PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlNote("extendedDesc")
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

    var details = getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    assertThat(details.getDescription().getRight().getTabs())
      .filteredOn(RuleDescriptionTabDto::getTitle, "More Info")
      .extracting(RuleDescriptionTabDto::getContent)
      .extracting(Either::getLeft)
      .extracting(RuleNonContextualSectionDto::getHtmlContent)
      .containsExactly("extendedDesc<h3>Clean Code Principles</h3>\n" +
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

  @SonarLintTest
  void it_should_split_security_hotspots_rule_description_and_adapt_title(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withSecurityHotspotsEnabled()
      .build();

    var details = getEffectiveRuleDetails(backend, "scopeId", "python:S4784");

    assertThat(details.getDescription().isRight()).isTrue();
    assertThat(details.getDescription().getRight().getTabs())
      .hasSize(3)
      .extracting(RuleDescriptionTabDto::getTitle)
      .contains("What's the risk?");
  }

  private SonarLintTestRpcServer prepareForRuleDescriptionSectionsAndContext(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet("python",
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withEnabledLanguageInStandaloneMode(PYTHON)
      .build();
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlNote("extendedDesc")
        .setEducationPrinciples(Rules.Rule.EducationPrinciples.newBuilder().addEducationPrinciples("never_trust_user_input").build())
        .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("introduction").setContent("intro content"))
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("how_to_fix").setContent("fix spring")
            .setContext(Rules.Rule.DescriptionSection.Context.newBuilder().setKey("spring").setDisplayName("Spring").build()).build())
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("how_to_fix").setContent("fix struts")
            .setContext(Rules.Rule.DescriptionSection.Context.newBuilder().setKey("struts").setDisplayName("Struts").build()).build())
          .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
            .setKey("resources").setContent("htmlContent3").build()))
        .build())
      .build());
    return backend;
  }

  private static List<String> flattenTabContent(RuleDescriptionTabDto tab) {
    List<String> result = new ArrayList<>();
    result.add(tab.getTitle());
    if (tab.getContent().isLeft()) {
      result.add(abbreviate(tab.getContent().getLeft().getHtmlContent(), 50));
    } else {
      tab.getContent().getRight().getContextualSections().forEach(s -> {
        result.add("--> " + s.getDisplayName() + " (" + s.getContextKey() + ")");
        result.add("    " + abbreviate(s.getHtmlContent(), 50));
      });
    }
    return result;
  }

  private EffectiveRuleDetailsDto getEffectiveRuleDetails(SonarLintTestRpcServer backend, String configScopeId, String ruleKey) {
    return getEffectiveRuleDetails(backend, configScopeId, ruleKey, null);
  }

  private EffectiveRuleDetailsDto getEffectiveRuleDetails(SonarLintTestRpcServer backend, String configScopeId, String ruleKey, String contextKey) {
    try {
      return backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, ruleKey, contextKey)).get().details();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

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
