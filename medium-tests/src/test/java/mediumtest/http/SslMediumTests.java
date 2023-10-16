/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.SonarLintTestRpcServer;
import nl.altindag.ssl.util.CertificateUtils;
import nl.altindag.ssl.util.KeyStoreUtils;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static testutils.TestUtils.protobufBody;

class SslMediumTests {

  public static final String KEYSTORE_PWD = "pwdServerP12";

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @AfterAll
  static void clearSonarCloudUrl() {
    System.clearProperty("sonarlint.internal.sonarcloud.url");
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing sonarcloudMock and mockSonarCloudUrl() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ServerCertificate {

    @RegisterExtension
    WireMockExtension sonarcloudMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicHttpsPort().httpDisabled(true)
        .keystoreType("pkcs12")
        .keystorePath(toPath(Objects.requireNonNull(SslMediumTests.class.getResource("/ssl/server.p12"))).toString())
        .keystorePassword(KEYSTORE_PWD)
        .keyManagerPassword(KEYSTORE_PWD))
      .build();

    @BeforeAll
    void mockSonarCloudUrl() {
      System.setProperty("sonarlint.internal.sonarcloud.url", sonarcloudMock.baseUrl());
    }

    @BeforeEach
    void prepare() {
      sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1")
        .willReturn(aResponse().withStatus(200)
          .withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder()
            .addOrganizations(Organizations.Organization.newBuilder()
              .setKey("myCustom")
              .setName("orgName")
              .setDescription("orgDesc")
              .build())
            .setPaging(Common.Paging.newBuilder()
              .setTotal(1)
              .setPageSize(1)
              .setPageIndex(1)
              .build())
            .build()))));
    }

    @Test
    void it_should_not_trust_server_self_signed_certificate_by_default() {
      var fakeClient = newFakeClient().build();
      backend = newBackend().build(fakeClient);

      var future = backend.getConnectionService().getOrganization(new GetOrganizationParams(Either.forLeft(new TokenDto("token")), "myOrg"));
      var thrown = assertThrows(CompletionException.class, future::join);
      assertThat(thrown).hasRootCauseInstanceOf(ResponseErrorException.class).hasRootCauseMessage("Internal error.");
      assertThat(future).isCompletedExceptionally();
    }

    @Test
    void it_should_ask_user_only_once_if_server_certificate_is_trusted() throws ExecutionException, InterruptedException, KeyStoreException {
      var sonarLintClientMock = mock(SonarLintRpcClient.class);
      var fakeClient = newFakeClient(sonarLintClientMock).build();

      backend = newBackend().build(fakeClient);

      var captor = ArgumentCaptor.forClass(CheckServerTrustedParams.class);

      when(sonarLintClientMock.checkServerTrusted(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new CheckServerTrustedResponse(true)));

      // Two concurrent requests should only trigger checkServerTrusted once
      var future = backend.getConnectionService().getOrganization(new GetOrganizationParams(Either.forLeft(new TokenDto("token")), "myOrg"));
      var future2 = backend.getConnectionService().getOrganization(new GetOrganizationParams(Either.forLeft(new TokenDto("token")), "myOrg"));

      future.get();
      future2.get();

      verify(sonarLintClientMock, times(1)).checkServerTrusted(any());

      var params = captor.getValue();

      assertThat(params.getAuthType()).isEqualTo("UNKNOWN");
      assertThat(params.getChain()).hasSize(1);
      var pems = CertificateUtils.parsePemCertificate(params.getChain().get(0).getPem());
      assertThat(pems).hasSize(1);
      assertThat(pems.get(0)).isInstanceOf(X509Certificate.class);

      var keyStore = KeyStoreUtils.loadKeyStore(backend.getUserHome().resolve("ssl/truststore.p12"), "sonarlint".toCharArray(), "PKCS12");
      assertThat(Collections.list(keyStore.aliases())).containsExactly("cn=localhost_o=sonarsource-sa_l=geneva_st=geneva_c=ch");

    }

  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing sonarcloudMock and mockSonarCloudUrl() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ClientCertificate {
    @RegisterExtension
    WireMockExtension sonarcloudMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicHttpsPort().httpDisabled(true)
        .keystoreType("pkcs12")
        .keystorePath(toPath(Objects.requireNonNull(SslMediumTests.class.getResource("/ssl/server.p12"))).toString())
        .keystorePassword(KEYSTORE_PWD)
        .keyManagerPassword(KEYSTORE_PWD)
        .needClientAuth(true)
        .trustStoreType("pkcs12")
        .trustStorePath(toPath(Objects.requireNonNull(SslMediumTests.class.getResource("/ssl/server-with-client-ca.p12"))).toString())
        .trustStorePassword("pwdServerWithClientCA"))
      .build();

    @BeforeAll
    void mockSonarCloudUrl() {
      System.setProperty("sonarlint.internal.sonarcloud.url", sonarcloudMock.baseUrl());
    }

    @BeforeEach
    void prepare() {
      sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1")
        .willReturn(aResponse().withStatus(200)
          .withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder()
            .addOrganizations(Organizations.Organization.newBuilder()
              .setKey("myCustom")
              .setName("orgName")
              .setDescription("orgDesc")
              .build())
            .setPaging(Common.Paging.newBuilder()
              .setTotal(1)
              .setPageSize(1)
              .setPageIndex(1)
              .build())
            .build()))));
    }

    @AfterEach
    void cleanup() {
      System.clearProperty("sonarlint.ssl.keyStorePath");
    }

    @Test
    void it_should_fail_if_client_certificate_not_provided() {
      var sonarLintClientMock = mock(SonarLintRpcClient.class);
      var fakeClient = newFakeClient(sonarLintClientMock).build();
      backend = newBackend().build(fakeClient);

      when(sonarLintClientMock.checkServerTrusted(any()))
        .thenReturn(CompletableFuture.completedFuture(new CheckServerTrustedResponse(true)));

      var future = backend.getConnectionService().getOrganization(new GetOrganizationParams(Either.forLeft(new TokenDto("token")), "myOrg"));

      var thrown = assertThrows(CompletionException.class, future::join);
      assertThat(thrown).hasRootCauseInstanceOf(ResponseErrorException.class).hasRootCauseMessage("Internal error.");
      assertThat(future).isCompletedExceptionally();

    }

    @Test
    void it_should_succeed_if_client_certificate_provided() throws ExecutionException, InterruptedException {

      System.setProperty("sonarlint.ssl.keyStorePath", toPath(Objects.requireNonNull(SslMediumTests.class.getResource("/ssl/client.p12"))).toString());
      System.setProperty("sonarlint.ssl.keyStorePassword", "pwdClientCertP12");
      var sonarLintClientMock = mock(SonarLintRpcClient.class);
      var fakeClient = newFakeClient(sonarLintClientMock).build();
      backend = newBackend().build(fakeClient);

      when(fakeClient.checkServerTrusted(any()))
        .thenReturn(CompletableFuture.completedFuture(new CheckServerTrustedResponse(true)));

      var future = backend.getConnectionService().getOrganization(new GetOrganizationParams(Either.forLeft(new TokenDto("token")), "myOrg"));

      assertThat(future).succeedsWithin(1, TimeUnit.MINUTES);
    }
  }

  private static Path toPath(URL url) {
    try {
      return Paths.get(url.toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

}
