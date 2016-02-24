/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.net.Proxy;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ServerConfiguration {

  public static final int DEFAULT_CONNECT_TIMEOUT_MILLISECONDS = 30_000;
  public static final int DEFAULT_READ_TIMEOUT_MILLISECONDS = 60_000;

  private final String url;
  private final String userAgent;
  private final String login;
  private final String password;
  private final Proxy proxy;
  private final String proxyLogin;
  private final String proxyPassword;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;

  private ServerConfiguration(Builder builder) {
    this.url = builder.url;
    this.userAgent = builder.userAgent;
    this.login = builder.login;
    this.password = builder.password;
    this.proxy = builder.proxy;
    this.proxyLogin = builder.proxyLogin;
    this.proxyPassword = builder.proxyPassword;
    this.connectTimeoutMs = builder.connectTimeoutMs;
    this.readTimeoutMs = builder.readTimeoutMs;
  }

  public String getUrl() {
    return url;
  }

  @CheckForNull
  public String getUserAgent() {
    return userAgent;
  }

  @CheckForNull
  public String getLogin() {
    return login;
  }

  @CheckForNull
  public String getPassword() {
    return password;
  }

  @CheckForNull
  public Proxy getProxy() {
    return proxy;
  }

  @CheckForNull
  public String getProxyLogin() {
    return proxyLogin;
  }

  @CheckForNull
  public String getProxyPassword() {
    return proxyPassword;
  }

  public int getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String url;
    private String userAgent;
    private String login;
    private String password;
    private Proxy proxy;
    private String proxyLogin;
    private String proxyPassword;
    private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLISECONDS;
    private int readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLISECONDS;

    private Builder() {
    }

    /**
     * Optional User  Agent
     */
    public Builder userAgent(@Nullable String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    /**
     * Mandatory HTTP server URL, eg "http://localhost:9000"
     */
    public Builder url(String url) {
      this.url = url;
      return this;
    }

    /**
     * Optional login/password, for example "admin"
     */
    public Builder credentials(@Nullable String login, @Nullable String password) {
      this.login = login;
      this.password = password;
      return this;
    }

    /**
     * Optional access token, for example {@code "ABCDE"}. Alternative to {@link #credentials(String, String)}
     */
    public Builder token(@Nullable String token) {
      this.login = token;
      this.password = null;
      return this;
    }

    /**
     * Sets a specified timeout value, in milliseconds, to be used when opening HTTP connection.
     * A timeout of zero is interpreted as an infinite timeout. Default value is {@link #DEFAULT_CONNECT_TIMEOUT_MILLISECONDS}
     */
    public Builder connectTimeoutMilliseconds(int i) {
      this.connectTimeoutMs = i;
      return this;
    }

    /**
     * Sets the read timeout to a specified timeout, in milliseconds.
     * A timeout of zero is interpreted as an infinite timeout. Default value is {@link #DEFAULT_READ_TIMEOUT_MILLISECONDS}
     */
    public Builder readTimeoutMilliseconds(int i) {
      this.readTimeoutMs = i;
      return this;
    }

    public Builder proxy(@Nullable Proxy proxy) {
      this.proxy = proxy;
      return this;
    }

    public Builder proxyCredentials(@Nullable String proxyLogin, @Nullable String proxyPassword) {
      this.proxyLogin = proxyLogin;
      this.proxyPassword = proxyPassword;
      return this;
    }

    public ServerConfiguration build() {
      if (url == null) {
        throw new UnsupportedOperationException("Server URL is mandatory");
      }
      if (userAgent == null) {
        throw new UnsupportedOperationException("User agent is mandatory");
      }
      return new ServerConfiguration(this);
    }

  }

}
