/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.Organizations;
import org.sonarqube.ws.Organizations.Organization;
import org.sonarqube.ws.Organizations.SearchWsResponse;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.validate.AuthenticationChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class WsHelperImplTest {
  public static final String PLUGINS_INSTALLED = "{\"plugins\": [\n" +
    "    {\n" +
    "      \"key\": \"branch\",\n" +
    "      \"filename\": \"sonar-branch-plugin-1.1.0.879.jar\",\n" +
    "      \"sonarLintSupported\": false,\n" +
    "      \"hash\": \"064d334d27aa14aab6e39315428ee3cf\",\n" +
    "      \"version\": \"1.1 (build 879)\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"key\": \"javascript\",\n" +
    "      \"filename\": \"sonar-javascript-plugin-3.4.0.5828.jar\",\n" +
    "      \"sonarLintSupported\": true,\n" +
    "      \"hash\": \"d136fdb31fe38c3d780650f7228a49fa\",\n" +
    "      \"version\": \"3.4 (build 5828)\"\n" +
    "    } ]}";

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
    WsClientTestUtils.addResponse(client, "api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");
    WsClientTestUtils.addResponse(client, PluginListDownloader.WS_PATH, PLUGINS_INSTALLED);
    WsClientTestUtils.addResponse(client, "api/authentication/validate?format=json", "{\"valid\": true}");
    ValidationResult validation = WsHelperImpl.validateConnection(client, null);
    assertThat(validation.success()).isTrue();

  }

  @Test
  public void testConnectionOrganizationNotFound() throws Exception {
    WsClientTestUtils.addResponse(client, "api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");
    WsClientTestUtils.addResponse(client, PluginListDownloader.WS_PATH, PLUGINS_INSTALLED);
    WsClientTestUtils.addResponse(client, "api/authentication/validate?format=json", "{\"valid\": true}");
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1", "/orgs/empty.pb");
    ValidationResult validation = WsHelperImpl.validateConnection(client, "myOrg");
    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("No organizations found for key: myOrg");
  }

  @Test
  public void testConnection_ok_with_org() throws Exception {
    WsClientTestUtils.addResponse(client, "api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");
    WsClientTestUtils.addResponse(client, PluginListDownloader.WS_PATH, PLUGINS_INSTALLED);
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
    assertThat(validation.message()).isEqualTo("SonarQube server has version 4.5. Version should be greater or equal to 6.7");
  }

  @Test
  public void testListUserOrganizationWithMoreThan20Pages() throws IOException {
    for (int i = 0; i < 21; i++) {
      mockOrganizationsPage(i + 1, 10500);
    }

    List<RemoteOrganization> orgs = WsHelperImpl.listUserOrganizations(client, serverChecker, new ProgressWrapper(null));
    assertThat(orgs).hasSize(10500);
  }

  private void mockOrganizationsPage(int page, int total) throws IOException {
    List<Organization> orgs = IntStream.rangeClosed(1, 500)
      .mapToObj(i -> Organization.newBuilder().setKey("org_page" + page + "number" + i).build())
      .collect(Collectors.toList());

    Paging paging = Paging.newBuilder()
      .setPageSize(500)
      .setTotal(total)
      .setPageIndex(page)
      .build();
    SearchWsResponse response = Organizations.SearchWsResponse.newBuilder()
      .setPaging(paging)
      .addAllOrganizations(orgs)
      .build();
    WsClientTestUtils.addResponse(client, "api/organizations/search.protobuf?member=true&ps=500&p=" + page, response);
  }

  @Test
  public void testListUserOrganizations() {
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?member=true&ps=500&p=1", "/orgs/orgsp1.pb");
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?member=true&ps=500&p=2", "/orgs/orgsp2.pb");
    WsClientTestUtils.addStreamResponse(client, "api/organizations/search.protobuf?member=true&ps=500&p=3", "/orgs/orgsp3.pb");
    List<RemoteOrganization> orgs = WsHelperImpl.listUserOrganizations(client, serverChecker, new ProgressWrapper(null));
    assertThat(orgs).hasSize(4);

    verify(serverChecker).checkVersionAndStatus();

    when(serverChecker.checkVersionAndStatus()).thenThrow(UnsupportedServerException.class);
    try {
      WsHelperImpl.listUserOrganizations(client, serverChecker, new ProgressWrapper(null));
      fail("Expected exception");
    } catch (UnsupportedServerException e) {
      // Success
    }
  }


}
