/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.AuthenticationChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionCheckerTest;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class WsHelperImplTest {
  private WsHelperImpl helper;

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
    client = WsClientTestUtils.createMock();
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
  public void testConnection_ok() throws Exception {
    WsClientTestUtils.addResponse(client, "api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.6\",\"status\": \"UP\"}");
    String content = Resources.toString(this.getClass().getResource(PluginVersionCheckerTest.RESPONSE_FILE_LTS), StandardCharsets.UTF_8);
    WsClientTestUtils.addResponse(client, PluginVersionChecker.WS_PATH_LTS, content);
    WsClientTestUtils.addResponse(client, "api/authentication/validate?format=json", "{\"valid\": true}");
    ValidationResult validation = WsHelperImpl.validateConnection(client, null);
    assertThat(validation.success()).isTrue();

  }

  @Test
  public void testConnectionUnsupportedOrganizations() throws Exception {
    WsClientTestUtils.addResponse(client, "api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.6\",\"status\": \"UP\"}");
    String content = Resources.toString(this.getClass().getResource(PluginVersionCheckerTest.RESPONSE_FILE_LTS), StandardCharsets.UTF_8);
    WsClientTestUtils.addResponse(client, PluginVersionChecker.WS_PATH_LTS, content);
    WsClientTestUtils.addResponse(client, "api/authentication/validate?format=json", "{\"valid\": true}");
    ValidationResult validation = WsHelperImpl.validateConnection(client, "myOrg");
    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("No organization support for this server version: 5.6");
  }

  @Test
  public void testConnectionOrganizationNotFound() throws Exception {
    WsClientTestUtils.addResponse(client, "api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.3\",\"status\": \"UP\"}");
    String content = Resources.toString(this.getClass().getResource(PluginVersionCheckerTest.RESPONSE_FILE_LTS), StandardCharsets.UTF_8);
    WsClientTestUtils.addResponse(client, PluginVersionChecker.WS_PATH_LTS, content);
    WsClientTestUtils.addResponse(client, "api/authentication/validate?format=json", "{\"valid\": true}");
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1", "/orgs/empty.pb");
    ValidationResult validation = WsHelperImpl.validateConnection(client, "myOrg");
    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("No organizations found for key: myOrg");
  }

  @Test
  public void testConnection_ok_with_org() throws Exception {
    WsClientTestUtils.addResponse(client, "api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.3\",\"status\": \"UP\"}");
    String content = Resources.toString(this.getClass().getResource(PluginVersionCheckerTest.RESPONSE_FILE_LTS), StandardCharsets.UTF_8);
    WsClientTestUtils.addResponse(client, PluginVersionChecker.WS_PATH_LTS, content);
    WsClientTestUtils.addResponse(client, "api/authentication/validate?format=json", "{\"valid\": true}");
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?organizations=henryju-github&ps=500&p=1", "/orgs/single.pb");
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?organizations=henryju-github&ps=500&p=2", "/orgs/empty.pb");
    ValidationResult validation = WsHelperImpl.validateConnection(client, "henryju-github");
    assertThat(validation.success()).isTrue();
  }

  @Test
  public void testUnsupportedServer() {
    WsClientTestUtils.addResponse(client, "api/system/status", "{\"id\": \"20160308094653\",\"version\": \"4.5\",\"status\": \"UP\"}");
    when(serverChecker.checkVersionAndStatus()).thenThrow(UnsupportedServerException.class);
    ValidationResult validation = WsHelperImpl.validateConnection(client, null);
    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("SonarQube server has version 4.5. Version should be greater or equal to 5.6");
  }

  @Test
  public void testListOrganizations() {
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?ps=500&p=1", "/orgs/orgsp1.pb");
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?ps=500&p=2", "/orgs/orgsp2.pb");
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?ps=500&p=3", "/orgs/orgsp3.pb");
    List<RemoteOrganization> orgs = WsHelperImpl.listOrganizations(client, serverChecker);
    assertThat(orgs).hasSize(4);

    verify(serverChecker).checkVersionAndStatus("6.3");

    when(serverChecker.checkVersionAndStatus("6.3")).thenThrow(UnsupportedServerException.class);
    try {
      WsHelperImpl.listOrganizations(client, serverChecker);
      fail("Expected exception");
    } catch (UnsupportedServerException e) {
      // Success
    }
  }

}
