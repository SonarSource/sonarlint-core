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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.InvalidAlgorithmParameterException;
import java.security.MessageDigest;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import nl.altindag.ssl.SSLFactory;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs in a separate JVM because the JDK caches AIA configuration and issuer certificates. */
public class CertificatePathValidationProbe {
  public static void main(String[] args) throws Exception {
    X509Certificate[] certificates;
    try (var input = CertificatePathValidationProbe.class.getResourceAsStream("/ssl/aia-chains.pem")) {
      certificates = CertificateFactory.getInstance("X.509").generateCertificates(input).toArray(X509Certificate[]::new);
    }
    // Fixture order: root, blocked CA, allowed CA, subordinate CA, and their three leaves.
    var root = certificates[0];
    var blocked = certificates[1];
    var downloaded = new HashSet<String>();
    if (args.length > 0 && "unavailable-issuer".equals(args[0])) {
      assertUnavailableIssuer(root, certificates[5], downloaded);
      return;
    }
    serveIssuers(Map.of("/blocked.der", blocked.getEncoded(), "/allowed.der", certificates[2].getEncoded(),
      "/subordinate.der", certificates[3].getEncoded()), downloaded);
    var delegate = SSLFactory.builder().withTrustMaterial(root).build().getTrustManager().orElseThrow();
    var blockedKeys = Set.of(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(blocked.getPublicKey().getEncoded())));
    var protectedManager = new BlockedCertificateTrustManager(delegate, blockedKeys);

    // First demonstrate that the original delegate accepts a leaf after downloading its issuer.
    delegate.checkServerTrusted(new X509Certificate[] {certificates[4]}, "RSA");
    assertThat(downloaded).contains("/blocked.der");
    assertBlocked(protectedManager, certificates[4]);
    // The blocked CA can also be above another missing intermediate.
    delegate.checkServerTrusted(new X509Certificate[] {certificates[6]}, "RSA");
    assertThat(downloaded).contains("/subordinate.der");
    assertBlocked(protectedManager, certificates[6]);

    var customTrust = SSLFactory.builder().withTrustMaterial(blocked).build().getTrustManager().orElseThrow();
    customTrust.checkServerTrusted(new X509Certificate[] {certificates[6]}, "RSA");
    var protectedCustomTrust = new BlockedCertificateTrustManager(customTrust, blockedKeys);
    assertThatThrownBy(() -> protectedCustomTrust.checkServerTrusted(new X509Certificate[] {certificates[6]}, "RSA"))
      .isInstanceOf(CertificateException.class)
      .hasMessage("Certificate path could not be validated")
      .hasCauseInstanceOf(InvalidAlgorithmParameterException.class);

    // Legitimate AIA retrieval must still work, including the client-validation overloads.
    var allowedChain = new X509Certificate[] {certificates[5]};
    protectedManager.checkServerTrusted(allowedChain, "RSA");
    protectedManager.checkServerTrusted(allowedChain, "RSA", (Socket) null);
    protectedManager.checkServerTrusted(allowedChain, "RSA", (SSLEngine) null);
    protectedManager.checkClientTrusted(allowedChain, "RSA");
    protectedManager.checkClientTrusted(allowedChain, "RSA", (Socket) null);
    protectedManager.checkClientTrusted(allowedChain, "RSA", (SSLEngine) null);
    assertThat(downloaded).contains("/allowed.der");
  }

  private static void assertBlocked(X509ExtendedTrustManager manager, X509Certificate leaf) {
    var chain = new X509Certificate[] {leaf};
    assertBlockedFailure(() -> manager.checkServerTrusted(chain, "RSA"));
    assertBlockedFailure(() -> manager.checkServerTrusted(chain, "RSA", (Socket) null));
    assertBlockedFailure(() -> manager.checkServerTrusted(chain, "RSA", (SSLEngine) null));
    assertBlockedFailure(() -> manager.checkClientTrusted(chain, "RSA"));
    assertBlockedFailure(() -> manager.checkClientTrusted(chain, "RSA", (Socket) null));
    assertBlockedFailure(() -> manager.checkClientTrusted(chain, "RSA", (SSLEngine) null));
  }

  private static void assertBlockedFailure(ThrowingCallable validation) {
    assertThatThrownBy(validation)
      .isInstanceOf(CertificateException.class)
      .hasMessage("Certificate path contains a blocked public key")
      .hasCauseInstanceOf(CertPathValidatorException.class)
      .satisfies(failure -> assertThat(((CertPathValidatorException) failure.getCause()).getReason())
        .isEqualTo(BlockedCertificateTrustManager.RejectionReason.BLOCKED_PUBLIC_KEY));
  }

  private static void assertUnavailableIssuer(X509Certificate root, X509Certificate leaf, Set<String> downloaded) throws Exception {
    serveIssuers(Map.of(), downloaded);
    // Let the outer path check handle this failure independently of the delegate's trust decision.
    var delegate = SSLFactory.builder().withTrustMaterial(root).withTrustEnhancer(parameters -> true).build().getTrustManager().orElseThrow();
    var manager = new BlockedCertificateTrustManager(delegate);
    assertThatThrownBy(() -> manager.checkServerTrusted(new X509Certificate[] {leaf}, "RSA"))
      .isInstanceOf(CertificateException.class)
      .hasMessage("Certificate path could not be validated")
      .hasCauseInstanceOf(CertPathBuilderException.class);
    assertThat(downloaded).contains("/allowed.der");
  }

  private static void serveIssuers(Map<String, byte[]> issuers, Set<String> downloaded) {
    // Intercept only this disposable JVM's AIA requests; no network connection is opened.
    URL.setURLStreamHandlerFactory(protocol -> "http".equals(protocol) ? new URLStreamHandler() {
      @Override
      protected URLConnection openConnection(URL url) throws IOException {
        if (!"certificates.test".equals(url.getHost())) {
          throw new IOException("Unexpected certificate URL: " + url);
        }
        downloaded.add(url.getPath());
        if (!issuers.containsKey(url.getPath())) {
          throw new IOException("Certificate issuer is unavailable");
        }
        return new HttpURLConnection(url) {
          @Override
          public void connect() {
            // Certificates are served from memory.
          }

          @Override
          public void disconnect() {
            // No network connection was opened.
          }

          @Override
          public boolean usingProxy() {
            return false;
          }

          @Override
          public int getResponseCode() {
            return HTTP_OK;
          }

          @Override
          public InputStream getInputStream() {
            return new ByteArrayInputStream(issuers.get(url.getPath()));
          }
        };
      }
    } : null);
  }
}
