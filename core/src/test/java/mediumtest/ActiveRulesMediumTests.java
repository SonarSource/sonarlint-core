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
package mediumtest;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.StorageFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import testutils.MockWebServerExtensionWithProtobuf;
import testutils.PluginLocator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveRulesMediumTests {

  @BeforeEach
  void prepare() {
    fakeClient = mock(SonarLintClient.class);
    when(fakeClient.getHttpClient("connectionId")).thenReturn(MockWebServerExtensionWithProtobuf.httpClient());

    backend = new SonarLintBackendImpl(fakeClient);
  }

  @AfterEach
  void tearDown() {
    backend.shutdown();
  }

  @Test
  void it_should_return_embedded_rule_when_project_is_not_bound(@TempDir Path tempDir) throws ExecutionException, InterruptedException {
    backend.initialize(new InitializeParams(tempDir, Set.of(PluginLocator.getPythonPluginPath()), emptyMap(), emptyMap(), Set.of(Language.PYTHON), null,
      Collections.emptyList(), Collections.emptyList()));
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("scopeId", null, true, "Scope", new BindingConfigurationDto(null, null, false)))));

    var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails("scopeId", "python:S139").get();

    var details = activeRuleDetailsResponse.details();
    assertThat(details).isNotNull();
    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description", "extendedDescription")
      .containsExactly("python:S139", "Comments should not be located at the end of lines of code", RuleType.CODE_SMELL, Language.PYTHON, IssueSeverity.MINOR,
        PYTHON_S139_DESCRIPTION, null);
    assertThat(details.getParams())
      .extracting("name", "description", "defaultValue")
      .containsExactly(tuple("legalTrailingCommentPattern", null, "^#\\s*+[^\\s]++$"));
  }

  @Test
  void it_should_fail_when_rule_key_unknown_and_project_is_not_bound(@TempDir Path tempDir) {
    backend.initialize(new InitializeParams(tempDir, Set.of(PluginLocator.getPythonPluginPath()), emptyMap(), emptyMap(), Set.of(Language.PYTHON), null,
      Collections.emptyList(), Collections.emptyList()));
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("scopeId", null, true, "Scope", new BindingConfigurationDto(null, null, false)))));

    var futureResponse = backend.getActiveRulesService().getActiveRuleDetails("scopeId", "python:SXXXX");

    assertThat(futureResponse).failsWithin(1, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(IllegalArgumentException.class)
      .withMessageContaining("Could not find rule 'python:SXXXX' in embedded rules");
  }

  @Test
  void it_should_return_rule_loaded_from_server_plugin_when_project_is_bound_and_project_storage_does_not_exist(@TempDir Path tempDir)
    throws ExecutionException, InterruptedException {
    StorageFixture.newStorage("connectionId")
      .withJavaPlugin()
      .create(tempDir);
    backend.initialize(new InitializeParams(tempDir.resolve("storage"), emptySet(), emptyMap(), emptyMap(), Set.of(Language.JAVA), null,
      Collections.emptyList(), Collections.emptyList()));
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("scopeId", null, true, "Scope", new BindingConfigurationDto("connectionId", "projectKey", false)))));

    var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails("scopeId", "java:S106").get();

    var details = activeRuleDetailsResponse.details();
    assertThat(details).isNotNull();
    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description", "extendedDescription")
      .containsExactly("java:S106", "Standard outputs should not be used directly to log anything", RuleType.CODE_SMELL, Language.JAVA, IssueSeverity.MAJOR,
        JAVA_S106_DESCRIPTION,
        null);
    assertThat(details.getParams()).isEmpty();
  }

  @Test
  void it_should_return_embedded_rule_when_project_is_bound_and_rule_comes_from_extra_plugin(@TempDir Path tempDir) throws ExecutionException, InterruptedException {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey")
      .create(tempDir);
    backend
      .initialize(new InitializeParams(tempDir.resolve("storage"), emptySet(), Map.of(Language.PYTHON.getPluginKey(), PluginLocator.getPythonPluginPath()), emptyMap(),
        Set.of(Language.PYTHON), null,
        Collections.emptyList(), Collections.emptyList()));
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("scopeId", null, true, "Scope", new BindingConfigurationDto("connectionId", "projectKey", false)))));

    var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails("scopeId", "python:S139").get();

    var details = activeRuleDetailsResponse.details();
    assertThat(details).isNotNull();
    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description", "extendedDescription")
      .containsExactly("python:S139", "Comments should not be located at the end of lines of code", RuleType.CODE_SMELL, Language.PYTHON, IssueSeverity.MINOR,
        PYTHON_S139_DESCRIPTION,
        null);
    assertThat(details.getParams())
      .extracting("name", "description", "defaultValue")
      .containsExactly(tuple("legalTrailingCommentPattern", null, "^#\\s*+[^\\s]++$"));
  }

  @Test
  void it_should_merge_rule_from_storage_and_server_when_project_is_bound(@TempDir Path tempDir) throws ExecutionException, InterruptedException {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(tempDir);
    backend
      .initialize(new InitializeParams(tempDir.resolve("storage"), emptySet(), Map.of(Language.PYTHON.getPluginKey(), PluginLocator.getPythonPluginPath()), emptyMap(),
        Set.of(Language.PYTHON), null,
        Collections.emptyList(), Collections.emptyList()));
    backend.getConnectionService().initialize(new org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams(
      List.of(new SonarQubeConnectionConfigurationDto("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())), emptyList()));
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("scopeId", null, true, "Scope", new BindingConfigurationDto("connectionId", "projectKey", false)))));
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc").build())
      .build());

    var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails("scopeId", "python:S139").get();

    var details = activeRuleDetailsResponse.details();
    assertThat(details).isNotNull();
    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description", "extendedDescription")
      .containsExactly("python:S139", "Comments should not be located at the end of lines of code", RuleType.CODE_SMELL, Language.PYTHON, IssueSeverity.INFO,
        PYTHON_S139_DESCRIPTION,
        "extendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @Test
  void it_should_fail_to_merge_rule_from_storage_and_server_when_connection_is_unknown(@TempDir Path tempDir) throws ExecutionException, InterruptedException {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(tempDir);
    backend
      .initialize(new InitializeParams(tempDir.resolve("storage"), emptySet(), Map.of(Language.PYTHON.getPluginKey(), PluginLocator.getPythonPluginPath()), emptyMap(),
        Set.of(Language.PYTHON), null,
        Collections.emptyList(), Collections.emptyList()));
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("scopeId", null, true, "Scope", new BindingConfigurationDto("connectionId", "projectKey", false)))));
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc").build())
      .build());

    var futureResponse = backend.getActiveRulesService().getActiveRuleDetails("scopeId", "python:S139");

    assertThat(futureResponse).failsWithin(1, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(IllegalStateException.class)
      .withMessageContaining("Unknown connection 'connectionId'");
  }

  @Test
  void it_should_fail_to_merge_rule_from_storage_and_server_when_rule_does_not_exist_on_server(@TempDir Path tempDir) {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(tempDir);
    backend
      .initialize(new InitializeParams(tempDir.resolve("storage"), emptySet(), Map.of(Language.PYTHON.getPluginKey(), PluginLocator.getPythonPluginPath()), emptyMap(),
        Set.of(Language.PYTHON), null,
        Collections.emptyList(), Collections.emptyList()));
    backend.getConnectionService().initialize(new org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams(
      List.of(new SonarQubeConnectionConfigurationDto("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())), emptyList()));
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("scopeId", null, true, "Scope", new BindingConfigurationDto("connectionId", "projectKey", false)))));

    var futureResponse = backend.getActiveRulesService().getActiveRuleDetails("scopeId", "python:S139");

    assertThat(futureResponse).failsWithin(3, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(IllegalStateException.class)
      .withMessageContaining("Could not find rule 'python:S139' on 'connectionId'");
  }

  @Test
  void it_should_merge_template_rule_from_storage_and_server_when_project_is_bound(@TempDir Path tempDir) throws ExecutionException, InterruptedException {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withCustomActiveRule("python:custom", "python:CommentRegularExpression", "INFO", Map.of("message", "msg", "regularExpression", "regExp"))))
      .create(tempDir);
    backend
      .initialize(new InitializeParams(tempDir.resolve("storage"), emptySet(), Map.of(Language.PYTHON.getPluginKey(), PluginLocator.getPythonPluginPath()), emptyMap(),
        Set.of(Language.PYTHON), null,
        Collections.emptyList(), Collections.emptyList()));
    backend.getConnectionService().initialize(new org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams(
      List.of(new SonarQubeConnectionConfigurationDto("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())), emptyList()));
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("scopeId", null, true, "Scope", new BindingConfigurationDto("connectionId", "projectKey", false)))));
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:custom", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc").build())
      .build());

    var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails("scopeId", "python:custom").get();

    var details = activeRuleDetailsResponse.details();
    assertThat(details).isNotNull();
    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description", "extendedDescription")
      .containsExactly("python:custom", "newName", RuleType.CODE_SMELL, Language.PYTHON, IssueSeverity.INFO, "desc", "extendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  @Test
  void it_should_merge_rule_from_storage_and_server_rule_when_rule_is_unknown_in_loaded_plugins(@TempDir Path tempDir)
    throws ExecutionException, InterruptedException {
    StorageFixture.newStorage("connectionId")
      .withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah"))))
      .create(tempDir);
    backend
      .initialize(new InitializeParams(tempDir.resolve("storage"), emptySet(), emptyMap(), emptyMap(),
        Set.of(Language.PYTHON), null,
        Collections.emptyList(), Collections.emptyList()));
    backend.getConnectionService().initialize(new org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams(
      List.of(new SonarQubeConnectionConfigurationDto("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())), emptyList()));
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("scopeId", null, true, "Scope", new BindingConfigurationDto("connectionId", "projectKey", false)))));
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=python:S139", Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc("desc").setHtmlNote("extendedDesc").build())
      .build());

    var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails("scopeId", "python:S139").get();

    var details = activeRuleDetailsResponse.details();
    assertThat(details).isNotNull();
    assertThat(details)
      .extracting("key", "name", "type", "language", "severity", "description", "extendedDescription")
      .containsExactly("python:S139", "newName", RuleType.BUG, Language.PYTHON, IssueSeverity.INFO, "desc", "extendedDesc");
    assertThat(details.getParams()).isEmpty();
  }

  private SonarLintClient fakeClient;
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
