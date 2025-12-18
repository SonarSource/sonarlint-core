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
package mediumtest.http;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.test.utils.ProtobufUtils.protobufBody;

class AuthenticationMediumTests {

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @SonarLintTest
  void it_should_authenticate_preemptively_on_sonarqube_with_login_password(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient()
      .withCredentials("connectionId", "myLogin", "myPassword")
      .build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(SonarLanguage.PYTHON.getSonarLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(fakeClient);
    sonarqubeMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": " +
        "\"UP\"}")));
    sonarqubeMock.stubFor(get("/api/rules/show.protobuf?key=python:S139")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlNote("extendedDesc from server").build())
        .build()))));

    getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/api/rules/show.protobuf?key=python:S139"))
      .withHeader("Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("myLogin:myPassword".getBytes(StandardCharsets.UTF_8)))));
  }

  @SonarLintTest
  void it_should_authenticate_preemptively_on_sonarqube_9_9_with_token_and_basic_scheme(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient()
      .withToken("connectionId", "myToken")
      .build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(SonarLanguage.PYTHON.getSonarLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(fakeClient);
    sonarqubeMock.stubFor(get("/api/rules/show.protobuf?key=python:S139")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlNote("extendedDesc from server").build())
        .build()))));
    sonarqubeMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": " +
        "\"UP\"}")));

    getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/api/rules/show.protobuf?key=python:S139"))
      .withHeader("Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("myToken:".getBytes(StandardCharsets.UTF_8)))));
  }

  @SonarLintTest
  void it_should_authenticate_preemptively_on_sonarqube_10_4_with_token_and_bearer_scheme(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient()
      .withToken("connectionId", "myToken")
      .build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(SonarLanguage.PYTHON.getSonarLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(fakeClient);
    sonarqubeMock.stubFor(get("/api/rules/show.protobuf?key=python:S139")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlNote("extendedDesc from server").build())
        .build()))));
    sonarqubeMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"10.4\",\"status\": " +
        "\"UP\"}")));

    getEffectiveRuleDetails(backend, "scopeId", "python:S139");

    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/api/rules/show.protobuf?key=python:S139"))
      .withHeader("Authorization", equalTo("Bearer myToken")));
  }

  @SonarLintTest
  void it_should_fail_the_request_if_credentials_are_not_returned_by_the_client(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getCredentials("connectionId")).thenReturn(null);
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(SonarLanguage.PYTHON.getSonarLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(fakeClient);
    sonarqubeMock.stubFor(get("/api/system/status").willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": \"UP\"}")));

    var throwable = catchThrowable(() -> getEffectiveRuleDetails(backend, "scopeId", "python:S139"));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .cause()
      .isInstanceOf(ResponseErrorException.class)
      .hasMessage("Internal error.");
  }

  @SonarLintTest
  void it_should_fail_the_request_if_dto_is_not_returned_by_the_client(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getCredentials("connectionId")).thenReturn(Either.forRight(null));
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(SonarLanguage.PYTHON.getSonarLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(fakeClient);
    sonarqubeMock.stubFor(get("/api/system/status").willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": \"UP\"}")));

    var throwable = catchThrowable(() -> getEffectiveRuleDetails(backend, "scopeId", "python:S139"));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .cause()
      .isInstanceOf(ResponseErrorException.class)
      .hasMessage("Internal error.");
  }

  @SonarLintTest
  void it_should_fail_the_request_if_token_is_not_returned_by_the_client(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getCredentials("connectionId")).thenReturn(Either.forLeft(new TokenDto(null)));
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(SonarLanguage.PYTHON.getSonarLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(fakeClient);
    sonarqubeMock.stubFor(get("/api/system/status").willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": \"UP\"}")));

    var throwable = catchThrowable(() -> getEffectiveRuleDetails(backend, "scopeId", "python:S139"));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .cause()
      .isInstanceOf(ResponseErrorException.class)
      .hasMessage("Internal error.");
  }

  @SonarLintTest
  void it_should_fail_the_request_if_username_is_not_returned_by_the_client(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getCredentials("connectionId")).thenReturn(Either.forRight(new UsernamePasswordDto(null, "pass")));
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(SonarLanguage.PYTHON.getSonarLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(fakeClient);
    sonarqubeMock.stubFor(get("/api/system/status").willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": \"UP\"}")));

    var throwable = catchThrowable(() -> getEffectiveRuleDetails(backend, "scopeId", "python:S139"));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .cause()
      .isInstanceOf(ResponseErrorException.class)
      .hasMessage("Internal error.");
  }

  @SonarLintTest
  void it_should_fail_the_request_if_password_is_not_returned_by_the_client(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getCredentials("connectionId")).thenReturn(Either.forRight(new UsernamePasswordDto("user", null)));
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(SonarLanguage.PYTHON.getSonarLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(fakeClient);
    sonarqubeMock.stubFor(get("/api/system/status").willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"10.8\",\"status\": \"UP\"}")));

    var throwable = catchThrowable(() -> getEffectiveRuleDetails(backend, "scopeId", "python:S139"));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .cause()
      .isInstanceOf(ResponseErrorException.class)
      .hasMessage("Internal error.");
  }

  private void getEffectiveRuleDetails(SonarLintTestRpcServer backend, String configScopeId, String ruleKey) {
    backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, ruleKey, null)).join().details();
  }

}
