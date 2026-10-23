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

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import nl.altindag.ssl.SSLFactory;

/** Applies mandatory public-key distrust to peer certificates and completed certificate paths. */
public final class BlockedCertificateTrustManager extends X509ExtendedTrustManager {
  // SHA-256 of SubjectPublicKeyInfo, derived from OpenJDK certificate trust data:
  // https://github.com/openjdk/jdk/commit/22bc7357ce6bc5f1ed2f1e83e0544b2c25f781d4
  // Matching keys also covers cross-signatures and alternate certificate encodings.
  private static final Set<String> BLOCKED_KEYS = Set.of(
    "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b",
    "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f",
    "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0",
    "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8",
    "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30",
    "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41",
    "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105",
    "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6");

  private final X509ExtendedTrustManager delegate;
  private final Set<String> blockedKeys;

  public BlockedCertificateTrustManager(X509ExtendedTrustManager delegate) {
    this(delegate, BLOCKED_KEYS);
  }

  BlockedCertificateTrustManager(X509ExtendedTrustManager delegate, Set<String> blockedKeys) {
    this.delegate = delegate;
    this.blockedKeys = Set.copyOf(blockedKeys);
  }

  public static SSLFactory protect(SSLFactory factory) {
    var builder = SSLFactory.builder()
      .withTrustMaterial(new BlockedCertificateTrustManager(factory.getTrustManager().orElseThrow()));
    factory.getKeyManager().ifPresent(builder::withIdentityMaterial);
    return builder.build();
  }

  private void checkBlockedKeys(@Nullable X509Certificate[] chain) throws CertificateException {
    if (chain == null || chain.length == 0) {
      throw new CertificateException("Certificate chain is empty");
    }
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (var certificate : chain) {
        var fingerprint = HexFormat.of().formatHex(digest.digest(certificate.getPublicKey().getEncoded()));
        if (blockedKeys.contains(fingerprint)) {
          throw new CertificateException("Certificate chain contains a blocked public key");
        }
      }
      // A locally trusted issuer may be omitted from the peer's chain.
      for (var issuer : delegate.getAcceptedIssuers()) {
        var fingerprint = HexFormat.of().formatHex(digest.digest(issuer.getPublicKey().getEncoded()));
        if (blockedKeys.contains(fingerprint)) {
          rejectCertificatesSignedBy(chain, issuer);
        }
      }
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for certificate validation", e);
    }
  }

  private static void rejectCertificatesSignedBy(X509Certificate[] chain, X509Certificate issuer) throws CertificateException {
    for (var certificate : chain) {
      if (certificate.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) {
        try {
          certificate.verify(issuer.getPublicKey());
        } catch (GeneralSecurityException e) {
          continue;
        }
        throw new CertificateException("Certificate chain contains a blocked public key");
      }
    }
  }

  private void checkCertificatePath(X509Certificate[] chain) throws CertificateException {
    var checker = new BlockedPublicKeyChecker();
    try {
      // Read the current issuers after delegation: custom trust can grow when a user approves a certificate.
      var anchors = Arrays.stream(delegate.getAcceptedIssuers())
        .filter(issuer -> !isBlocked(issuer))
        .map(issuer -> new TrustAnchor(issuer, null))
        .collect(Collectors.toSet());
      var target = new X509CertSelector();
      target.setCertificate(chain[0]);
      var parameters = new PKIXBuilderParameters(anchors, target);
      // The delegate already applied its revocation and TLS-specific validation policies.
      parameters.setRevocationEnabled(false);
      parameters.setMaxPathLength(-1);
      parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(Arrays.asList(chain))));
      parameters.addCertPathChecker(checker);
      // AIA can supply intermediates that were absent from both the peer chain and the trust store.
      CertPathBuilder.getInstance("PKIX").build(parameters);
    } catch (CertPathBuilderException e) {
      var rejection = checker.rejection.get();
      if (rejection != null) {
        var failure = new CertificateException(rejection.getMessage(), rejection);
        failure.addSuppressed(e);
        throw failure;
      }
      throw new CertificateException("Certificate path could not be validated", e);
    } catch (GeneralSecurityException e) {
      throw new CertificateException("Certificate path could not be validated", e);
    }
  }

  private boolean isBlocked(X509Certificate certificate) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return blockedKeys.contains(HexFormat.of().formatHex(digest.digest(certificate.getPublicKey().getEncoded())));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for certificate validation", e);
    }
  }

  enum RejectionReason implements CertPathValidatorException.Reason {
    BLOCKED_PUBLIC_KEY
  }

  private final class BlockedPublicKeyChecker extends PKIXCertPathChecker {
    // PKIX clones its checkers and can discard their exceptions when it backtracks.
    private final AtomicReference<CertPathValidatorException> rejection = new AtomicReference<>();

    @Override
    public void init(boolean forward) {
      // Public-key checks do not depend on traversal direction or previous certificates.
    }

    @Override
    public boolean isForwardCheckingSupported() {
      return true;
    }

    @Override
    public Set<String> getSupportedExtensions() {
      return Set.of();
    }

    @Override
    public void check(Certificate certificate, Collection<String> unresolvedCritExts) throws CertPathValidatorException {
      if (isBlocked((X509Certificate) certificate)) {
        var failure = new CertPathValidatorException("Certificate path contains a blocked public key", null, null, -1, RejectionReason.BLOCKED_PUBLIC_KEY);
        rejection.compareAndSet(null, failure);
        throw failure;
      }
    }
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkServerTrusted(chain, authType);
    checkCertificatePath(chain);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkServerTrusted(chain, authType, socket);
    checkCertificatePath(chain);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkServerTrusted(chain, authType, engine);
    checkCertificatePath(chain);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkClientTrusted(chain, authType);
    checkCertificatePath(chain);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkClientTrusted(chain, authType, socket);
    checkCertificatePath(chain);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkClientTrusted(chain, authType, engine);
    checkCertificatePath(chain);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return delegate.getAcceptedIssuers();
  }
}
