/*
 * SonarLint Core - Medium Tests
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
package mediumtest.analysis;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ActiveRuleDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RulesInConnectedModeMediumTests {

  private static final String CONFIG_SCOPE_ID = "config scope id";
  private static final String CONNECTION_ID = "myConnection";
  private static final String JAVA_MODULE_KEY = "sample-project";
  private SonarLintTestRpcServer backend;

  @AfterEach
  void stopBackend() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void should_ignore_unknown_active_rule_parameters_and_convert_deprecated_keys() throws Exception {
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA)
      .withSonarQubeConnection(CONNECTION_ID)
      .withStorage(CONNECTION_ID, s -> s
        .withPlugins(TestPlugin.JAVA)
        .withProject(JAVA_MODULE_KEY, project -> project
          .withRuleSet("java", ruleSet -> ruleSet
            // Emulate server returning a deprecated key for local analyzer
            .withActiveRule("squid:S106", "BLOCKER")
            .withActiveRule("java:S3776", "BLOCKER", Map.of("blah", "blah"))
            // Emulate server returning a deprecated template key
            .withCustomActiveRule("squid:myCustomRule", "squid:S124", "MAJOR", Map.of("message", "Needs to be reviewed", "regularExpression", ".*REVIEW.*")))))
      .build();

    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "My project",
        new BindingConfigurationDto(CONNECTION_ID, JAVA_MODULE_KEY, true)))));

    var activeRules = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).get().getActiveRules();
    assertThat(activeRules).extracting(ActiveRuleDto::getRuleKey, ActiveRuleDto::getLanguageKey, ActiveRuleDto::getParams, ActiveRuleDto::getTemplateRuleKey)
      .containsExactlyInAnyOrder(
        // Deprecated key has been converted
        tuple("java:S106", "java", Map.of(), null),
        // Unknown parameters have been removed
        tuple("java:S3776", "java", Map.of("Threshold", "15"), null),
        // Deprecated template key has been converted
        tuple("java:myCustomRule", "java", Map.of("message", "Needs to be reviewed", "regularExpression", ".*REVIEW.*"), "java:S124"));
  }

  @Test
  void secrets_rules_should_always_be_active_with_legacy_sonarqube() throws Exception {
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA)
      .withEnabledLanguageInStandaloneMode(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.SECRETS)
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .withSonarQubeConnection(CONNECTION_ID)
      .withStorage(CONNECTION_ID, s -> s
        .withPlugins(TestPlugin.JAVA)
        .withServerVersion("9.8")
        .withProject(JAVA_MODULE_KEY))
      .build();

    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "My project",
        new BindingConfigurationDto(CONNECTION_ID, JAVA_MODULE_KEY, true)))));

    var activeRules = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).get().getActiveRules();
    assertThat(activeRules).extracting(ActiveRuleDto::getRuleKey, ActiveRuleDto::getLanguageKey).contains(
      tuple("secrets:S6292", "secrets"));
  }

  @Test
  void hotspot_rules_should_be_active_when_feature_flag_is_enabled() throws Exception {
    backend = newBackend()
      .withSecurityHotspotsEnabled()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeConnection(CONNECTION_ID,
        storage -> storage.withServerVersion("9.7").withProject(JAVA_MODULE_KEY, project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S4792", "INFO"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, JAVA_MODULE_KEY)
      .build();

    var activeRules = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).get().getActiveRules();

    assertThat(activeRules)
      .extracting(ActiveRuleDto::getRuleKey)
      .contains("java:S4792");
  }

  @Test
  void hotspot_rules_should_not_be_active_when_feature_flag_is_disabled() throws Exception {
    backend = newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeConnection(CONNECTION_ID,
        storage -> storage.withServerVersion("9.7").withProject(JAVA_MODULE_KEY, project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S4792", "INFO"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, JAVA_MODULE_KEY)
      .build();

    var activeRules = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).get().getActiveRules();

    assertThat(activeRules).isEmpty();
  }

}
