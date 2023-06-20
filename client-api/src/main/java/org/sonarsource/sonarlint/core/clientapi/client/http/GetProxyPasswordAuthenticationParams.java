/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.client.http;

import java.net.Authenticator;
import java.net.InetAddress;

/**
 * @see Authenticator#requestPasswordAuthentication(String, InetAddress, int, String, String, String)
 *
 */
public class GetProxyPasswordAuthenticationParams {

  private final String host;
  private final int port;
  private final String protocol;
  private final String prompt;
  private final String scheme;

  private final String targetHostURL;

  public GetProxyPasswordAuthenticationParams(String host, int port, String protocol, String prompt, String scheme, String targetHostURL) {
    this.host = host;
    this.port = port;
    this.protocol = protocol;
    this.prompt = prompt;
    this.scheme = scheme;
    this.targetHostURL = targetHostURL;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getProtocol() {
    return protocol;
  }

  public String getPrompt() {
    return prompt;
  }

  public String getScheme() {
    return scheme;
  }

  public String getTargetHostURL() {
    return targetHostURL;
  }
}
