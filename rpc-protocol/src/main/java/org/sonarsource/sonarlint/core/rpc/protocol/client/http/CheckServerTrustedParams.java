/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.http;

import java.util.List;

public class CheckServerTrustedParams {

  /**
   * the peer certificate chain
   */
  private final List<X509CertificateDto> chain;

  /**
   * the key exchange algorithm used
   */
  private final String authType;

  public CheckServerTrustedParams(List<X509CertificateDto> chain, String authType) {
    this.chain = chain;
    this.authType = authType;
  }

  public List<X509CertificateDto> getChain() {
    return chain;
  }

  public String getAuthType() {
    return authType;
  }
}
