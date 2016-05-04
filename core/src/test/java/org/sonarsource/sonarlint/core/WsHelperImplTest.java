/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.AuthenticationChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class WsHelperImplTest {
  private WsHelperImpl helper;

  @Mock
  private SonarLintWsClient client;
  @Mock
  private ServerVersionAndStatusChecker serverChecker;
  @Mock
  private PluginVersionChecker pluginChecker;
  @Mock
  private AuthenticationChecker authChecker;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    helper = new WsHelperImpl();
  }

  @Test(expected = NullPointerException.class)
  public void testNullServerConfig() {
    helper.validateConnection((ServerConfiguration) null);
  }

  @Test(expected = NullPointerException.class)
  public void testNullServerConfigToken() {
    helper.generateAuthenticationToken((ServerConfiguration) null, "name", true);
  }

  @Test
  public void createToken() {
    String response = "{\n" +
      "  \"login\": \"token\",\n" +
      "  \"name\": \"Third Party Application\",\n" +
      "  \"token\": \"123456789\"\n" +
      "}";
    WsClientTestUtils.addPostResponse(client, "api/user_tokens/generate?name=token", response);
    String token = WsHelperImpl.generateAuthenticationToken(serverChecker, client, "token", true);
    verify(serverChecker).checkVersionAndStatus("5.4");
    verify(client).post("api/user_tokens/revoke?name=token");
    verify(client).post("api/user_tokens/generate?name=token");
    verifyNoMoreInteractions(client);

    assertThat(token).isEqualTo("123456789");
  }

  @Test
  public void createTokenDontForce() {
    String response = "{\n" +
      "  \"login\": \"token\",\n" +
      "  \"name\": \"Third Party Application\",\n" +
      "  \"token\": \"123456789\"\n" +
      "}";
    WsClientTestUtils.addPostResponse(client, "api/user_tokens/generate?name=token", response);
    String token = WsHelperImpl.generateAuthenticationToken(serverChecker, client, "token", false);
    verify(serverChecker).checkVersionAndStatus("5.4");
    verify(client).post("api/user_tokens/generate?name=token");
    verifyNoMoreInteractions(client);

    assertThat(token).isEqualTo("123456789");
  }

  @Test
  public void testConnection() {
    ServerInfos serverInfo = ServerInfos.newBuilder()
      .setStatus("OK")
      .setVersion("5.5")
      .build();
    when(serverChecker.checkVersionAndStatus()).thenReturn(serverInfo);
    WsHelperImpl.validateConnection(serverChecker, pluginChecker, authChecker);
    verify(serverChecker).checkVersionAndStatus();
    verify(pluginChecker).checkPlugins();
    verify(authChecker).validateCredentials();
  }

  @Test
  public void testUnsupportedServer() {
    when(serverChecker.checkVersionAndStatus()).thenThrow(UnsupportedServerException.class);
    ValidationResult validation = WsHelperImpl.validateConnection(serverChecker, pluginChecker, authChecker);
    verify(serverChecker).checkVersionAndStatus();
    assertThat(validation.success()).isFalse();
  }

}
