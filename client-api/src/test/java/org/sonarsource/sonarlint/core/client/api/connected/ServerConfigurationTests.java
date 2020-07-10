/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.net.Proxy;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

class ServerConfigurationTests {

  @Test
  void builder_url_mandatory() {
    try {
      ServerConfiguration.builder().build();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Server URL is mandatory");
    }
  }

  @Test
  void builder_user_agent_mandatory() {
    try {
      ServerConfiguration.builder()
        .url("http://foo")
        .build();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("User agent is mandatory");
    }
  }

  @Test
  void equals_hash() {
    ServerConfiguration config1 = ServerConfiguration.builder()
      .url("http://foo")
      .userAgent("agent")
      .build();

    ServerConfiguration config2 = ServerConfiguration.builder()
      .url("http://foo")
      .userAgent("agent2")
      .build();

    ServerConfiguration config3 = ServerConfiguration.builder()
      .url("http://foo")
      .userAgent("agent2")
      .proxyCredentials("proxyLogin", "proxyPass")
      .build();
    
    ServerConfiguration config4 = ServerConfiguration.builder()
      .url("http://foo")
      .userAgent("agent")
      .build();

    assertThat(config1.equals(config2)).isFalse();
    assertThat(config1.equals(config3)).isFalse();
    assertThat(config1.equals(new Object())).isFalse();
    assertThat(config1.equals(config1)).isTrue();
    assertThat(config1.equals(config4)).isTrue();
    assertThat(config1.hashCode()).isEqualTo(config4.hashCode());



  }

  @Test
  void minimal_builder() {
    ServerConfiguration config = ServerConfiguration.builder()
      .url("http://foo")
      .userAgent("agent")
      .build();
    assertThat(config.getUrl()).isEqualTo("http://foo");
    assertThat(config.getUserAgent()).isEqualTo("agent");
  }

  @Test
  void max_builder() {
    Proxy proxy = mock(Proxy.class);
    X509TrustManager trustManager = mock(X509TrustManager.class);
    SSLSocketFactory socketFactory = mock(SSLSocketFactory.class);
    ServerConfiguration config = ServerConfiguration.builder()
      .url("http://foo")
      .userAgent("agent")
      .credentials("user", "pwd")
      .proxy(proxy)
      .sslSocketFactory(socketFactory)
      .trustManager(trustManager)
      .proxyCredentials("proxyUser", "proxyPwd")
      .readTimeoutMilliseconds(10)
      .connectTimeoutMilliseconds(20)
      .organizationKey("org")
      .build();
    assertThat(config.getSSLSocketFactory()).isEqualTo(socketFactory);
    assertThat(config.getTrustManager()).isEqualTo(trustManager);
    assertThat(config.getUrl()).isEqualTo("http://foo");
    assertThat(config.getUserAgent()).isEqualTo("agent");
    assertThat(config.getLogin()).isEqualTo("user");
    assertThat(config.getPassword()).isEqualTo("pwd");
    assertThat(config.getProxy()).isEqualTo(proxy);
    assertThat(config.getProxyLogin()).isEqualTo("proxyUser");
    assertThat(config.getProxyPassword()).isEqualTo("proxyPwd");
    assertThat(config.getReadTimeoutMs()).isEqualTo(10);
    assertThat(config.getConnectTimeoutMs()).isEqualTo(20);
    assertThat(config.getOrganizationKey()).isEqualTo("org");
  }

  @Test
  void use_token() {
    ServerConfiguration config = ServerConfiguration.builder()
      .url("http://foo")
      .userAgent("agent")
      .token("foo")
      .build();
    assertThat(config.getUrl()).isEqualTo("http://foo");
    assertThat(config.getUserAgent()).isEqualTo("agent");
    assertThat(config.getLogin()).isEqualTo("foo");
    assertThat(config.getPassword()).isNull();
  }
}
