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

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import nl.altindag.ssl.model.TrustManagerParameters;
import nl.altindag.ssl.util.CertificateUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;

@Named
@Singleton
public class AskClientCertificatePredicate implements Predicate<TrustManagerParameters> {

  private final SonarLintRpcClient client;

  public AskClientCertificatePredicate(SonarLintRpcClient client) {
    this.client = client;
  }

  @Override
  public boolean test(TrustManagerParameters trustManagerParameters) {
    try {
      return client
        .checkServerTrusted(new CheckServerTrustedParams(
          Arrays.stream(trustManagerParameters.getChain())
            .map(c -> new X509CertificateDto(CertificateUtils.convertToPem(c))).collect(Collectors.toList()),
          trustManagerParameters.getAuthType()))
        .get()
        .isTrusted();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }
}
