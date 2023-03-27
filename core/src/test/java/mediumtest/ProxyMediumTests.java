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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestBackend;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static testutils.TestUtils.protobufBody;

class ProxyMediumTests {

  public static final String PROXY_AUTH_ENABLED = "proxy-auth";
  private SonarLintTestBackend backend;

  @RegisterExtension
  WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @RegisterExtension
  WireMockExtension proxyMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @BeforeEach
  void configureProxy(TestInfo info) {
    if (info.getTags().contains(PROXY_AUTH_ENABLED)) {
      proxyMock.stubFor(get(urlMatching(".*"))
        .inScenario("Proxy Auth")
        .whenScenarioStateIs(STARTED)
        .willReturn(aResponse()
          .withStatus(407)
          .withHeader("Proxy-Authenticate", "Basic realm=\"Access to the proxy\""))
        .willSetStateTo("Challenge returned"));
      proxyMock.stubFor(get(urlMatching(".*"))
        .inScenario("Proxy Auth")
        .whenScenarioStateIs("Challenge returned")
        .willReturn(aResponse().proxiedFrom(sonarqubeMock.baseUrl())));
    } else {
      proxyMock.stubFor(get(urlMatching(".*")).willReturn(aResponse().proxiedFrom(sonarqubeMock.baseUrl())));
    }
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void it_should_honor_http_proxy_settings() {
    var fakeClient = newFakeClient()
      .withHttpProxy("localhost", proxyMock.getPort())
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(fakeClient);
    sonarqubeMock.stubFor(get("/api/rules/show.protobuf?key=python:S139")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc(
          "desc").setHtmlNote("extendedDesc from server").build())
        .build()))));

    var details = getEffectiveRuleDetails("scopeId", "python:S139");

    assertThat(details.getDescription().getLeft().getHtmlContent()).contains("extendedDesc from server");

    proxyMock.verify(getRequestedFor(urlEqualTo("/api/rules/show.protobuf?key=python:S139")));
  }

  @Test
  @Tag(PROXY_AUTH_ENABLED)
  void it_should_honor_http_proxy_authentication() {
    var fakeClient = newFakeClient()
      .withHttpProxy("localhost", proxyMock.getPort())
      .withHttpProxyAuth("proxyLogin", "proxyPassword")
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(fakeClient);
    sonarqubeMock.stubFor(get("/api/rules/show.protobuf?key=python:S139")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc(
          "desc").setHtmlNote("extendedDesc from server").build())
        .build()))));

    var details = getEffectiveRuleDetails("scopeId", "python:S139");

    assertThat(details.getDescription().getLeft().getHtmlContent()).contains("extendedDesc from server");

    proxyMock.verify(getRequestedFor(urlEqualTo("/api/rules/show.protobuf?key=python:S139"))
      .withHeader("Proxy-Authorization", containing("Basic")));
  }

  private EffectiveRuleDetailsDto getEffectiveRuleDetails(String configScopeId, String ruleKey) {
    try {
      return this.backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, ruleKey, null)).get().details();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
