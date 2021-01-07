/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.net.Proxy;

import javax.annotation.CheckForNull;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class TelemetryClientConfig {
  private final String userAgent;
  private final Proxy proxy;
  private final String proxyLogin;
  private final String proxyPassword;
  private final SSLSocketFactory sslSocketFactory;
  private final X509TrustManager trustManager;

  public TelemetryClientConfig(Builder builder) {
    this.userAgent = builder.userAgent;
    this.proxyLogin = builder.proxyLogin;
    this.proxyPassword = builder.proxyPassword;
    this.sslSocketFactory = builder.sslSocketFactory;
    this.trustManager = builder.sslTrustManager;
    this.proxy = builder.proxy;
  }

  public String userAgent() {
    return userAgent;
  }

  public Proxy proxy() {
    return proxy;
  }

  @CheckForNull
  public String proxyLogin() {
    return proxyLogin;
  }

  @CheckForNull
  public String proxyPassword() {
    return proxyPassword;
  }

  @CheckForNull
  public SSLSocketFactory sslSocketFactory() {
    return sslSocketFactory;
  }

  @CheckForNull
  public X509TrustManager trustManager() {
    return trustManager;
  }
  
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String userAgent;
    private Proxy proxy;
    private String proxyLogin;
    private String proxyPassword;
    private SSLSocketFactory sslSocketFactory = null;
    private X509TrustManager sslTrustManager = null;

    @CheckForNull
    public String userAgent() {
      return userAgent;
    }

    public Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    @CheckForNull
    public Proxy proxy() {
      return proxy;
    }

    public Builder proxy(Proxy proxy) {
      this.proxy = proxy;
      return this;
    }

    @CheckForNull
    public String proxyLogin() {
      return proxyLogin;
    }

    public Builder proxyLogin(String proxyLogin) {
      this.proxyLogin = proxyLogin;
      return this;
    }

    @CheckForNull
    public String proxyPassword() {
      return proxyPassword;
    }

    public Builder proxyPassword(String proxyPassword) {
      this.proxyPassword = proxyPassword;
      return this;
    }

    @CheckForNull
    public SSLSocketFactory sslSocketFactory() {
      return sslSocketFactory;
    }

    public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory) {
      this.sslSocketFactory = sslSocketFactory;
      return this;
    }

    @CheckForNull
    public X509TrustManager sslTrustManager() {
      return sslTrustManager;
    }

    public Builder sslTrustManager(X509TrustManager sslTrustManager) {
      this.sslTrustManager = sslTrustManager;
      return this;
    }

    public TelemetryClientConfig build() {
      return new TelemetryClientConfig(this);
    }
  }
}
