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
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestBackend;
import mediumtest.fixtures.StorageFixture;
import mediumtest.fixtures.TestPlugin;
import nl.altindag.ssl.util.CertificateUtils;
import nl.altindag.ssl.util.KeyStoreUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedResponse;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static testutils.TestUtils.protobufBody;

class SslMediumTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  @RegisterExtension
  WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicHttpsPort())
    .build();

  private SonarLintTestBackend backend;

  @BeforeEach
  void prepare() {
    sonarqubeMock.stubFor(get("/api/rules/show.protobuf?key=python:S139")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder().setName("newName").setSeverity("INFO").setType(Common.RuleType.BUG).setLang("py").setHtmlDesc(
          "desc").setHtmlNote("extendedDesc from server").build())
        .build()))));
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void it_should_not_trust_self_signed_certificate() {
    var fakeClient = newFakeClient()
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(fakeClient);

    var future = this.backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("scopeId", "python:S139", null));
    var thrown = assertThrows(CompletionException.class, future::join);
    assertThat(thrown).hasRootCauseInstanceOf(java.security.cert.CertificateException.class).hasRootCauseMessage("None of the TrustManagers trust this certificate chain");
    assertThat(future).isCompletedExceptionally();
  }

  @Test
  void it_should_trust_user_certificate_after_asking_once() throws ExecutionException, InterruptedException, KeyStoreException {
    var fakeClient = newFakeClient()
      .build();
    fakeClient = Mockito.spy(fakeClient);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", sonarqubeMock.baseUrl(), storage -> storage.withProject("projectKey",
        projectStorage -> projectStorage.withRuleSet(Language.PYTHON.getLanguageKey(),
          ruleSet -> ruleSet.withActiveRule("python:S139", "INFO", Map.of("legalTrailingCommentPattern", "blah")))))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(fakeClient);

    var captor = ArgumentCaptor.forClass(CheckServerTrustedParams.class);

    when(fakeClient.checkServerTrusted(captor.capture()))
      .thenReturn(CompletableFuture.completedFuture(new CheckServerTrustedResponse(true)));

    // Two concurrent requests should only trigger checkServerTrusted once
    var future = this.backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("scopeId", "python:S139", null));
    var future2 = this.backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("scopeId", "python:S139", null));

    future.get();
    future2.get();

    verify(fakeClient, times(1)).checkServerTrusted(any());

    var params = captor.getValue();

    assertThat(params.getAuthType()).isEqualTo("UNKNOWN");
    assertThat(params.getChain()).hasSize(1);

    var keyStore = KeyStoreUtils.loadKeyStore(backend.getUserHome().resolve("ssl/truststore.jks"), "changeit".toCharArray());
    assertThat(Collections.list(keyStore.aliases())).containsExactly("cn=tom-akehurst_ou=unknown_o=unknown_l=unknown_st=unknown_c=unknown");

  }

}
