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
package org.sonarsource.sonarlint.core.http;

import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.X509TrustManager;
import nl.altindag.ssl.util.CertificateUtils;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.X509CertificateDto;

@Named
@Singleton
public class ConfirmingTrustManager implements X509TrustManager {
  private final MutableTrustManager mutableTrustManager;

  private static final String KEYSTORE_PWD = System.getProperty("sonarlint.ssl.trustStorePassword", "sonarlint");
  private static final X509Certificate FAKE_CERTIFICATE = new NoopX509Certificate();

  private final SonarLintClient client;

  public ConfirmingTrustManager(@Named("userHome") Path sonarlintUserHome, SonarLintClient client) {
    this.client = client;
    var sonarlintTruststore = sonarlintUserHome.resolve("ssl/truststore.p12");
    mutableTrustManager = new MutableTrustManager(sonarlintTruststore, KEYSTORE_PWD);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    mutableTrustManager.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    synchronized (this) {
      try {
        mutableTrustManager.checkServerTrusted(chain, authType);
      } catch (CertificateException e) {
        var isTrustedByClient = false;
        try {
          isTrustedByClient = client
            .checkServerTrusted(new CheckServerTrustedParams(
              Arrays.stream(chain)
                .map(c -> new X509CertificateDto(CertificateUtils.convertToPem(c))).collect(Collectors.toList()),
              authType))
            .get()
            .isTrusted();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        } catch (ExecutionException ex) {
          throw new RuntimeException(ex);
        }
        if (isTrustedByClient) {
          final X509Certificate endPoint = chain[0];
          mutableTrustManager.addCertificate(endPoint);
        } else {
          throw e;
        }
      }
    }
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    // We have to return a certificate here or our TrustManager will be ignored by SSLContext
    // See https://github.com/Hakky54/sslcontext-kickstart/discussions/337
    return new X509Certificate[] {FAKE_CERTIFICATE};
  }

}
