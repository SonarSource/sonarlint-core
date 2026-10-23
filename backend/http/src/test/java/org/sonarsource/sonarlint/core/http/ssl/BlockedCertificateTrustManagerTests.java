/*
 * SonarLint Core - HTTP
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.http.ssl;

import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import nl.altindag.ssl.SSLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BlockedCertificateTrustManagerTests {
  private final X509ExtendedTrustManager delegate = mock(X509ExtendedTrustManager.class);
  private BlockedCertificateTrustManager underTest;

  @BeforeEach
  void prepareDelegate() {
    when(delegate.getAcceptedIssuers()).thenReturn(new X509Certificate[0]);
    underTest = new BlockedCertificateTrustManager(delegate);
    clearInvocations(delegate);
  }

  @Test
  void it_should_block_all_published_certificates_before_delegating() throws Exception {
    try (var input = getClass().getResourceAsStream("/ssl/blocked-intermediates.pem")) {
      var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
      assertThat(certificates).hasSize(16);
      for (var certificate : certificates) {
        var chain = new X509Certificate[] {(X509Certificate) certificate};
        assertBlocked(chain);
        // A blocked intermediate must also be rejected after an unrelated leaf.
        assertBlocked(new X509Certificate[] {trustedCertificate(), (X509Certificate) certificate});
      }
    }
    verifyNoInteractions(delegate);
  }

  private void assertBlocked(X509Certificate[] chain) {
    assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "EC")).isInstanceOf(CertificateException.class).hasMessageContaining("blocked public key");
    assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "EC", (Socket) null)).isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "EC", (SSLEngine) null)).isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> underTest.checkClientTrusted(chain, "EC")).isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> underTest.checkClientTrusted(chain, "EC", (Socket) null)).isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> underTest.checkClientTrusted(chain, "EC", (SSLEngine) null)).isInstanceOf(CertificateException.class);
  }

  @Test
  void it_should_preserve_every_delegate_validation_and_accepted_issuers() throws Exception {
    var chain = new X509Certificate[] {trustedCertificate()};
    when(delegate.getAcceptedIssuers()).thenReturn(chain);
    var socket = mock(Socket.class);
    var engine = mock(SSLEngine.class);
    underTest.checkServerTrusted(chain, "RSA");
    underTest.checkServerTrusted(chain, "RSA", socket);
    underTest.checkServerTrusted(chain, "RSA", engine);
    underTest.checkClientTrusted(chain, "RSA");
    underTest.checkClientTrusted(chain, "RSA", socket);
    underTest.checkClientTrusted(chain, "RSA", engine);
    verify(delegate).checkServerTrusted(chain, "RSA");
    verify(delegate).checkServerTrusted(chain, "RSA", socket);
    verify(delegate).checkServerTrusted(chain, "RSA", engine);
    verify(delegate).checkClientTrusted(chain, "RSA");
    verify(delegate).checkClientTrusted(chain, "RSA", socket);
    verify(delegate).checkClientTrusted(chain, "RSA", engine);
    assertThat(underTest.getAcceptedIssuers()).isSameAs(chain);
  }

  @Test
  void it_should_preserve_validation_failures() throws Exception {
    var chain = new X509Certificate[] {trustedCertificate()};
    var failure = new CertificateException("Untrusted chain");
    doThrow(failure).when(delegate).checkServerTrusted(chain, "RSA");
    assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "RSA")).isSameAs(failure);
  }

  @Test
  void it_should_reject_missing_chains() {
    for (var chain : new X509Certificate[][] {null, new X509Certificate[0]}) {
      assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "RSA")).isInstanceOf(CertificateException.class);
    }
    verifyNoInteractions(delegate);
  }

  @Test
  void it_should_wrap_the_composite_including_permissive_custom_trust() throws Exception {
    var factory = BlockedCertificateTrustManager.protect(SSLFactory.builder().withTrustMaterial(delegate).build());
    clearInvocations(delegate);
    try (var input = getClass().getResourceAsStream("/ssl/blocked-intermediates.pem")) {
      var certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
      assertThatThrownBy(() -> factory.getTrustManager().orElseThrow().checkServerTrusted(new X509Certificate[] {certificate}, "EC"))
        .isInstanceOf(CertificateException.class).hasMessageContaining("blocked public key");
    }
    verifyNoInteractions(delegate);
  }

  @Test
  void it_should_reject_a_blocked_trust_anchor_omitted_from_the_peer_chain() throws Exception {
    try (var input = getClass().getResourceAsStream("/ssl/blocked-intermediates.pem")) {
      var issuer = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
      var leaf = mock(X509Certificate.class);
      when(leaf.getPublicKey()).thenReturn(trustedCertificate().getPublicKey());
      when(leaf.getIssuerX500Principal()).thenReturn(issuer.getSubjectX500Principal());
      when(delegate.getAcceptedIssuers()).thenReturn(new X509Certificate[] {issuer});
      underTest = new BlockedCertificateTrustManager(delegate);
      assertThatThrownBy(() -> underTest.checkServerTrusted(new X509Certificate[] {leaf}, "EC"))
        .isInstanceOf(CertificateException.class).hasMessageContaining("blocked public key");
      verify(leaf).verify(issuer.getPublicKey());
    }
  }

  @Test
  void it_should_not_rehash_trusted_issuers_for_each_handshake() throws Exception {
    var issuer = spy(trustedCertificate());
    when(delegate.getAcceptedIssuers()).thenReturn(new X509Certificate[] {issuer});
    var manager = new BlockedCertificateTrustManager(delegate);
    var chain = new X509Certificate[] {trustedCertificate()};
    var failure = new CertificateException("Untrusted chain");
    doThrow(failure).when(delegate).checkServerTrusted(chain, "RSA");
    clearInvocations(issuer);

    assertThatThrownBy(() -> manager.checkServerTrusted(chain, "RSA")).isSameAs(failure);
    assertThatThrownBy(() -> manager.checkServerTrusted(chain, "RSA")).isSameAs(failure);

    verify(issuer, never()).getPublicKey();
    verify(delegate, times(1)).getAcceptedIssuers();
  }

  @Test
  void it_should_stop_using_removed_trust_anchors() throws Exception {
    var chain = new X509Certificate[] {trustedCertificate()};
    when(delegate.getAcceptedIssuers()).thenReturn(chain);
    var manager = new BlockedCertificateTrustManager(delegate);
    manager.checkServerTrusted(chain, "RSA");

    when(delegate.getAcceptedIssuers()).thenReturn(new X509Certificate[0]);

    assertThatThrownBy(() -> manager.checkServerTrusted(chain, "RSA"))
      .isInstanceOf(CertificateException.class)
      .hasMessage("Certificate path could not be validated")
      .hasCauseInstanceOf(InvalidAlgorithmParameterException.class);
  }

  @Test
  void it_should_refresh_blocked_issuers_added_during_delegate_validation() throws Exception {
    try (var input = getClass().getResourceAsStream("/ssl/aia-chains.pem")) {
      var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input).toArray(X509Certificate[]::new);
      var blocked = certificates[1];
      var chain = new X509Certificate[] {certificates[4]};
      var blockedKeys = Set.of(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(blocked.getPublicKey().getEncoded())));
      when(delegate.getAcceptedIssuers()).thenReturn(new X509Certificate[] {certificates[0]});
      var manager = new BlockedCertificateTrustManager(delegate, blockedKeys);
      doAnswer(invocation -> {
        when(delegate.getAcceptedIssuers()).thenReturn(new X509Certificate[] {blocked});
        return null;
      }).when(delegate).checkServerTrusted(chain, "RSA");

      assertThatThrownBy(() -> manager.checkServerTrusted(chain, "RSA"))
        .isInstanceOf(CertificateException.class).hasMessage("Certificate path contains a blocked public key");
      assertThatThrownBy(() -> manager.checkServerTrusted(chain, "RSA"))
        .isInstanceOf(CertificateException.class).hasMessage("Certificate chain contains a blocked public key");
      verify(delegate, times(1)).checkServerTrusted(chain, "RSA");
    }
  }

  @Test
  void it_should_preserve_client_identity() {
    var keyManager = mock(X509ExtendedKeyManager.class);
    var original = SSLFactory.builder().withTrustMaterial(delegate).withIdentityMaterial(keyManager).build();
    var protectedFactory = BlockedCertificateTrustManager.protect(original);
    assertThat(protectedFactory.getKeyManager()).isEqualTo(original.getKeyManager());
  }

  @Test
  void it_should_accept_a_valid_chain_to_a_custom_root() throws Exception {
    try (var input = getClass().getResourceAsStream("/ssl/aia-chains.pem")) {
      var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input).toArray(X509Certificate[]::new);
      var original = SSLFactory.builder().withTrustMaterial(certificates[0]).build();
      var manager = BlockedCertificateTrustManager.protect(original).getTrustManager().orElseThrow();
      manager.checkServerTrusted(new X509Certificate[] {certificates[5], certificates[2]}, "RSA");
    }
  }

  @Test
  void it_should_preserve_custom_certificate_approval(@TempDir Path tempDir) throws Exception {
    var trustStore = tempDir.resolve("truststore.p12");
    var original = SSLFactory.builder()
      .withInflatableTrustMaterial(trustStore, "changeit".toCharArray(), "PKCS12", parameters -> true)
      .build();
    var manager = BlockedCertificateTrustManager.protect(original).getTrustManager().orElseThrow();
    try (var input = getClass().getResourceAsStream("/ssl/aia-chains.pem")) {
      var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input).toArray(X509Certificate[]::new);
      var chain = new X509Certificate[] {certificates[5]};
      manager.checkServerTrusted(chain, "RSA");
      assertThat(manager.getAcceptedIssuers()).containsExactly(chain);
      assertThat(trustStore).exists();
      manager.checkServerTrusted(chain, "RSA");
    }
  }

  @Test
  void it_should_reject_a_chain_without_a_trusted_path_even_if_the_delegate_accepts_it() {
    var chain = new X509Certificate[] {trustedCertificate()};
    assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "RSA"))
      .isInstanceOf(CertificateException.class)
      .hasMessage("Certificate path could not be validated")
      .hasCauseInstanceOf(InvalidAlgorithmParameterException.class);
  }

  @Test
  void it_should_validate_downloaded_intermediates(@TempDir Path tempDir) throws Exception {
    runPathValidationProbe(tempDir, "available-issuers");
  }

  @Test
  void it_should_report_an_unavailable_issuer_as_a_path_failure(@TempDir Path tempDir) throws Exception {
    runPathValidationProbe(tempDir, "unavailable-issuer");
  }

  @Test
  void it_should_report_blocked_trust_anchors_as_policy_rejections(@TempDir Path tempDir) throws Exception {
    runPathValidationProbe(tempDir, "blocked-trust-anchors");
  }

  private static void runPathValidationProbe(Path tempDir, String scenario) throws Exception {
    // AIA configuration is cached by the JDK, so enable it in a separate JVM.
    var output = tempDir.resolve("certificate-path-validation.log");
    var executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var command = new ArrayList<>(List.of(executable,
      "-Dcom.sun.security.enableAIAcaIssuers=true",
      "-Dcom.sun.security.allowedAIALocations=http://certificates.test/",
      "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
      CertificatePathValidationProbe.class.getName(), scenario));
    // Include the regression paths exercised in the child JVM in coverage reports.
    command.addAll(1, ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
      .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
      .toList());
    var process = new ProcessBuilder(command)
      .redirectErrorStream(true)
      .redirectOutput(output.toFile())
      .start();
    try {
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("Certificate path validation probe completed").isTrue();
      assertThat(process.exitValue()).withFailMessage("Certificate path validation probe failed:%n%s", Files.readString(output)).isZero();
    } finally {
      process.destroyForcibly();
    }
  }

  private static X509Certificate trustedCertificate() {
    return SSLFactory.builder().withDefaultTrustMaterial().build().getTrustedCertificates().getFirst();
  }
}
