package org.sonarsource.sonarlint.core.clientapi.client.http;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.URL;

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

  public GetProxyPasswordAuthenticationParams(String host, int port, String protocol, String prompt, String scheme) {
    this.host = host;
    this.port = port;
    this.protocol = protocol;
    this.prompt = prompt;
    this.scheme = scheme;
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
}
