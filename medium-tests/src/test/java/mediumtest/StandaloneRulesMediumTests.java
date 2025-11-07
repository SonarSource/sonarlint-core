/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ListAllStandaloneRulesDefinitionsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class StandaloneRulesMediumTests {

  @SonarLintTest
  void it_should_return_only_embedded_rules_of_enabled_languages(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withStandaloneEmbeddedPlugin(TestPlugin.PHP)
      .start();

    var allRules = listAllStandaloneRulesDefinitions(backend).getRulesByKey().values();

    assertThat(allRules).extracting(RuleDefinitionDto::getLanguage).containsOnly(Language.PYTHON);
  }

  @SonarLintTest
  void it_should_return_param_definition(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .start();

    var javaS1176 = listAllStandaloneRulesDefinitions(backend).getRulesByKey().get("java:S1176");

    assertThat(javaS1176.getParamsByKey()).containsOnlyKeys("exclusion", "forClasses");
    assertThat(javaS1176.getParamsByKey().get("forClasses"))
      .extracting(RuleParamDefinitionDto::getKey, RuleParamDefinitionDto::getName, RuleParamDefinitionDto::getDescription,
        RuleParamDefinitionDto::getType, RuleParamDefinitionDto::isMultiple, RuleParamDefinitionDto::getPossibleValues, RuleParamDefinitionDto::getDefaultValue)
      .containsExactly("forClasses",
        "forClasses",
        "Pattern of classes which should adhere to this constraint. Ex : **.api.**",
        RuleParamType.STRING,
        false,
        List.of(),
        "**.api.**");
  }

  @SonarLintTest
  void it_should_return_rule_details_with_definition_and_description(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .start();

    var ruleDetails = backend.getRulesService().getStandaloneRuleDetails(new GetStandaloneRuleDescriptionParams("java:S1176")).get();

    assertThat(ruleDetails.getRuleDefinition().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.CLEAR);
    assertThat(ruleDetails.getRuleDefinition().getSoftwareImpacts())
      .extracting(ImpactDto::getSoftwareQuality, ImpactDto::getImpactSeverity)
      .containsExactly(tuple(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.MEDIUM));
    assertThat(ruleDetails.getRuleDefinition().getName()).isEqualTo("Public types, methods and fields (API) should be documented with Javadoc");
    assertThat(ruleDetails.getDescription().isRight()).isTrue();
    assertThat(ruleDetails.getDescription().getRight().getIntroductionHtmlContent())
      .startsWith("<p>A good API documentation is a key factor in the usability and success of a software API");
  }

  @SonarLintTest
  void it_should_not_contain_rule_templates(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start();

    var allRules = listAllStandaloneRulesDefinitions(backend).getRulesByKey().values();

    assertThat(allRules).extracting(RuleDefinitionDto::getKey).isNotEmpty().doesNotContain("python:XPath");
    assertThat(backend.getRulesService().getStandaloneRuleDetails(new GetStandaloneRuleDescriptionParams("python:XPath"))).failsWithin(1, TimeUnit.MINUTES);
  }

  @SonarLintTest
  void it_should_not_contain_hotspots_by_default(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .start();

    var allRules = listAllStandaloneRulesDefinitions(backend).getRulesByKey().values();

    assertThat(allRules).extracting(RuleDefinitionDto::getKey).isNotEmpty().doesNotContain("java:S1313");
    assertThat(backend.getRulesService().getStandaloneRuleDetails(new GetStandaloneRuleDescriptionParams("java:S1313"))).failsWithin(1, TimeUnit.MINUTES);
  }

  private ListAllStandaloneRulesDefinitionsResponse listAllStandaloneRulesDefinitions(SonarLintTestRpcServer backend) {
    try {
      return backend.getRulesService().listAllStandaloneRulesDefinitions().get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
