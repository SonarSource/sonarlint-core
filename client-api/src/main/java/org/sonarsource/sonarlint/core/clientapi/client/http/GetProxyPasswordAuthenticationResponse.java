package org.sonarsource.sonarlint.core.clientapi.client.http;

import javax.annotation.Nullable;

public class GetProxyPasswordAuthenticationResponse {

  private final String proxyUser;
  private final String proxyPassword;

  public GetProxyPasswordAuthenticationResponse(@Nullable String proxyUser, @Nullable String proxyPassword) {
    this.proxyUser = proxyUser;
    this.proxyPassword = proxyPassword;
  }

  public String getProxyUser() {
    return proxyUser;
  }

  public String getProxyPassword() {
    return proxyPassword;
  }
}
