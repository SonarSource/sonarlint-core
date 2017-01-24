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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.WsComponents.Component;
import org.sonarqube.ws.WsComponents.TreeWsResponse;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.container.connected.update.ModuleHierarchyDownloader.PAGE_SIZE;

public class ModuleHierarchyDownloaderTest {
  private SonarLintWsClient wsClient;
  private ModuleHierarchyDownloader downloader;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    wsClient = mock(SonarLintWsClient.class);

    downloader = new ModuleHierarchyDownloader(wsClient);
  }

  @Test
  public void simpleTest() {
    WsClientTestUtils
      .addStreamResponse(wsClient, "api/components/tree.protobuf?ps=500&p=1&qualifiers=BRC&baseComponentKey=testRoot",
        "/update/tree.pb");
    WsClientTestUtils.addStreamResponse(wsClient, "api/components/show.protobuf?id=AVeumyM5SDj1uJJMtck2",
      "/update/show_module1.pb");
    WsClientTestUtils.addStreamResponse(wsClient, "api/components/show.protobuf?id=AVeumyM5SDj1uJJMtck3",
      "/update/show_module1_module11.pb");
    WsClientTestUtils.addStreamResponse(wsClient, "api/components/show.protobuf?id=AVeumyM5SDj1uJJMtck6",
      "/update/show_module1_module12.pb");
    WsClientTestUtils.addStreamResponse(wsClient, "api/components/show.protobuf?id=AVeumyM5SDj1uJJMtck9",
      "/update/show_module2.pb");
    Map<String, String> fetchModuleHierarchy = downloader.fetchModuleHierarchy("testRoot");
    assertThat(fetchModuleHierarchy).contains(
      entry("testRoot", ""),
      entry("testRoot:module1", "module1"),
      entry("testRoot:module2", "module2"),
      entry("testRoot:module1:module12", "module1/module12"),
      entry("testRoot:module1:module11", "module1/module11"));
  }

  @Test
  public void testNoPaginationWhenJustUnderPageSize() throws IOException {
    TreeWsResponse.Builder responseBuilder = TreeWsResponse.newBuilder()
      .setPaging(Paging.newBuilder().setTotal(PAGE_SIZE));
    for (int i = 0; i < PAGE_SIZE; i++) {
      responseBuilder.addComponents(Component.newBuilder()
        .setId("id" + i)
        .setKey("testRoot" + i));
      WsClientTestUtils.addStreamResponse(wsClient, "api/components/show.protobuf?id=id" + i, "/update/show_module1.pb");
    }

    WsClientTestUtils
      .addResponse(wsClient, "api/components/tree.protobuf?ps=500&p=1&qualifiers=BRC&baseComponentKey=testRoot", responseBuilder.build());

    Map<String, String> fetchModuleHierarchy = downloader.fetchModuleHierarchy("testRoot");
    assertThat(fetchModuleHierarchy).hasSize(PAGE_SIZE + 1 /* root module */);
  }

  @Test
  public void testPagination() throws IOException {
    TreeWsResponse.Builder responseBuilder = TreeWsResponse.newBuilder()
      .setPaging(Paging.newBuilder().setPageIndex(1).setTotal(501));
    for (int i = 0; i < PAGE_SIZE; i++) {
      responseBuilder.addComponents(Component.newBuilder()
        .setId("id" + i)
        .setKey("testRoot" + i));
      WsClientTestUtils.addStreamResponse(wsClient, "api/components/show.protobuf?id=id" + i, "/update/show_module1.pb");
    }

    WsClientTestUtils
      .addResponse(wsClient, "api/components/tree.protobuf?ps=500&p=1&qualifiers=BRC&baseComponentKey=testRoot", responseBuilder.build());

    responseBuilder = TreeWsResponse.newBuilder()
      .setPaging(Paging.newBuilder().setPageIndex(2).setTotal(501));
    for (int i = 501; i < 502; i++) {
      responseBuilder.addComponents(Component.newBuilder()
        .setId("id" + i)
        .setKey("testRoot" + i));
      WsClientTestUtils.addStreamResponse(wsClient, "api/components/show.protobuf?id=id" + i, "/update/show_module1.pb");
    }
    WsClientTestUtils
      .addResponse(wsClient, "api/components/tree.protobuf?ps=500&p=2&qualifiers=BRC&baseComponentKey=testRoot", responseBuilder.build());

    Map<String, String> fetchModuleHierarchy = downloader.fetchModuleHierarchy("testRoot");
    assertThat(fetchModuleHierarchy).hasSize(501 + 1);
  }

  @Test
  public void testIOException() {
    wsClient = mock(SonarLintWsClient.class);
    WsClientTestUtils.addFailedResponse(wsClient, "api/components/tree.protobuf?ps=500&p=1&qualifiers=BRC&baseComponentKey=testRoot", 503, "error");
    downloader = new ModuleHierarchyDownloader(wsClient);
    exception.expect(IllegalStateException.class);
    exception.expectMessage("Error 503");
    downloader.fetchModuleHierarchy("testRoot");
  }

  @Test
  public void testInvalidResponseContent() {
    wsClient = mock(SonarLintWsClient.class);
    WsClientTestUtils
      .addResponse(wsClient, "api/components/tree.protobuf?ps=500&p=1&qualifiers=BRC&baseComponentKey=testRoot", "invalid response stream");
    downloader = new ModuleHierarchyDownloader(wsClient);
    exception.expect(IllegalStateException.class);
    exception.expectMessage("Failed to load module hierarchy");
    downloader.fetchModuleHierarchy("testRoot");
  }
}
