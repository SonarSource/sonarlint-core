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

import java.util.Map;
import mockwebserver3.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.Components.Component;
import org.sonarqube.ws.Components.TreeWsResponse;
import org.sonarsource.sonarlint.core.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarlint.core.container.connected.update.ModuleHierarchyDownloader.PAGE_SIZE;

class ModuleHierarchyDownloaderTests {

  private static final ProgressMonitor PROGRESS = new ProgressMonitor(null);

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private ModuleHierarchyDownloader underTest;

  @BeforeEach
  void setUp() {
    underTest = new ModuleHierarchyDownloader();
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
    var fetchModuleHierarchy = underTest.fetchModuleHierarchy(mockServer.serverApiHelper(), "testRoot", PROGRESS);
    assertThat(fetchModuleHierarchy).contains(
      entry("testRoot", ""),
      entry("testRoot:module1", "module1"),
      entry("testRoot:module2", "module2"),
      entry("testRoot:module1:module12", "module1/module12"),
      entry("testRoot:module1:module11", "module1/module11"));
  }

  @Test
  void testNoPaginationWhenJustUnderPageSize() {
    var responseBuilder = TreeWsResponse.newBuilder()
      .setPaging(Paging.newBuilder().setTotal(PAGE_SIZE));
    for (var i = 0; i < PAGE_SIZE; i++) {
      responseBuilder.addComponents(Component.newBuilder()
        .setKey("testRoot" + i));
      mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot" + i, "/update/show_module1.pb");
    }

    mockServer.addProtobufResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=1", responseBuilder.build());

    var fetchModuleHierarchy = underTest.fetchModuleHierarchy(mockServer.serverApiHelper(), "testRoot", PROGRESS);
    assertThat(fetchModuleHierarchy).hasSize(PAGE_SIZE + 1 /* root module */);
  }

  @Test
  void testPagination() {
    var responseBuilder = TreeWsResponse.newBuilder()
      .setPaging(Paging.newBuilder().setPageIndex(1).setTotal(501));
    for (var i = 0; i < PAGE_SIZE; i++) {
      responseBuilder.addComponents(Component.newBuilder()
        .setKey("testRoot" + i));
      mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot" + i, "/update/show_module1.pb");
    }

    mockServer.addProtobufResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=1", responseBuilder.build());

    responseBuilder = TreeWsResponse.newBuilder()
      .setPaging(Paging.newBuilder().setPageIndex(2).setTotal(501));
    for (var i = 501; i < 502; i++) {
      responseBuilder.addComponents(Component.newBuilder()
        .setKey("testRoot" + i));
      mockServer.addResponseFromResource("/api/components/show.protobuf?component=testRoot" + i, "/update/show_module1.pb");
    }
    mockServer.addProtobufResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=2", responseBuilder.build());

    var fetchModuleHierarchy = underTest.fetchModuleHierarchy(mockServer.serverApiHelper(), "testRoot", PROGRESS);
    assertThat(fetchModuleHierarchy).hasSize(501 + 1);
  }

  @Test
  void testIOException() {
    mockServer.addResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=1", new MockResponse().setResponseCode(503));
    var thrown = assertThrows(IllegalStateException.class, () -> underTest.fetchModuleHierarchy(mockServer.serverApiHelper(), "testRoot", PROGRESS));
    assertThat(thrown).hasMessageContaining("Error 503");
  }

  @Test
  void testInvalidResponseContent() {
    mockServer.addStringResponse("/api/components/tree.protobuf?qualifiers=BRC&component=testRoot&ps=500&p=1", "invalid response stream");
    var thrown = assertThrows(IllegalStateException.class, () -> underTest.fetchModuleHierarchy(mockServer.serverApiHelper(), "testRoot", PROGRESS));
    assertThat(thrown).hasMessageContaining(" While parsing a protocol message, the input ended unexpectedly in the middle of a field.");
  }
}
