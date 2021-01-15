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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.Components.Component;
import org.sonarqube.ws.Components.TreeWsResponse;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarlint.core.container.connected.update.ModuleHierarchyDownloader.PAGE_SIZE;

class ModuleHierarchyDownloaderTests {

  private static final ProgressWrapper PROGRESS = new ProgressWrapper(null);

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private ModuleHierarchyDownloader underTest;

  @BeforeEach
  public void setUp() {
    underTest = new ModuleHierarchyDownloader(mockServer.slClient());
  }

  @Test
  void simpleTest() {
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=1",
      "/update/tree.pb");
    mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot%3Amodule1",
      "/update/show_module1.pb");
    mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot%3Amodule1%3Amodule11",
      "/update/show_module1_module11.pb");
    mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot%3Amodule1%3Amodule12",
      "/update/show_module1_module12.pb");
    mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot%3Amodule2",
      "/update/show_module2.pb");
    Map<String, String> fetchModuleHierarchy = underTest.fetchModuleHierarchy("testRoot", PROGRESS);
    assertThat(fetchModuleHierarchy).contains(
      entry("testRoot", ""),
      entry("testRoot:module1", "module1"),
      entry("testRoot:module2", "module2"),
      entry("testRoot:module1:module12", "module1/module12"),
      entry("testRoot:module1:module11", "module1/module11"));
  }

  @Test
  void testNoPaginationWhenJustUnderPageSize() throws IOException {
    TreeWsResponse.Builder responseBuilder = TreeWsResponse.newBuilder()
      .setPaging(Paging.newBuilder().setTotal(PAGE_SIZE));
    for (int i = 0; i < PAGE_SIZE; i++) {
      responseBuilder.addComponents(Component.newBuilder()
        .setKey("testRoot" + i));
      mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot" + i, "/update/show_module1.pb");
    }

    mockServer.addProtobufResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=1", responseBuilder.build());

    Map<String, String> fetchModuleHierarchy = underTest.fetchModuleHierarchy("testRoot", PROGRESS);
    assertThat(fetchModuleHierarchy).hasSize(PAGE_SIZE + 1 /* root module */);
  }

  @Test
  void testPagination() throws IOException {
    TreeWsResponse.Builder responseBuilder = TreeWsResponse.newBuilder()
      .setPaging(Paging.newBuilder().setPageIndex(1).setTotal(501));
    for (int i = 0; i < PAGE_SIZE; i++) {
      responseBuilder.addComponents(Component.newBuilder()
        .setKey("testRoot" + i));
      mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot" + i, "/update/show_module1.pb");
    }

    mockServer.addProtobufResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=1", responseBuilder.build());

    responseBuilder = TreeWsResponse.newBuilder()
      .setPaging(Paging.newBuilder().setPageIndex(2).setTotal(501));
    for (int i = 501; i < 502; i++) {
      responseBuilder.addComponents(Component.newBuilder()
        .setKey("testRoot" + i));
      mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot" + i, "/update/show_module1.pb");
    }
    mockServer.addProtobufResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=2", responseBuilder.build());

    Map<String, String> fetchModuleHierarchy = underTest.fetchModuleHierarchy("testRoot", PROGRESS);
    assertThat(fetchModuleHierarchy).hasSize(501 + 1);
  }

  @Test
  void testIOException() {
    mockServer.addResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=1", new MockResponse().setResponseCode(503));
    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.fetchModuleHierarchy("testRoot", PROGRESS));
    assertThat(thrown).hasMessageContaining("Error 503");
  }

  @Test
  void testInvalidResponseContent() {
    mockServer.addStringResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=1", "invalid response stream");
    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.fetchModuleHierarchy("testRoot", PROGRESS));
    assertThat(thrown).hasMessageContaining(" While parsing a protocol message, the input ended unexpectedly in the middle of a field.");
  }
}
