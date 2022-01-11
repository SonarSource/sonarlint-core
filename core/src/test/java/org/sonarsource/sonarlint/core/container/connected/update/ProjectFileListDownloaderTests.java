/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProjectFileListDownloaderTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private final static String PROJECT_KEY = "project1";

  private final ProgressWrapper progressWrapper = mock(ProgressWrapper.class);

  private ProjectFileListDownloader underTest;

  @BeforeEach
  public void setUp() {
    underTest = new ProjectFileListDownloader(mockServer.serverApiHelper());
  }

  @Test
  void should_get_files() throws IOException {
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&ps=500&p=1", "/update/component_tree.pb");

    List<String> files = underTest.get(PROJECT_KEY, progressWrapper);

    assertThat(files).hasSize(187);
    assertThat(files.get(0)).isEqualTo("org.sonarsource.sonarlint.intellij:sonarlint-intellij:src/main/java/org/sonarlint/intellij/ui/AbstractIssuesPanel.java");
  }

  @Test
  void should_get_files_with_organization() throws IOException {
    underTest = new ProjectFileListDownloader(mockServer.serverApiHelper("myorg"));
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&organization=myorg&ps=500&p=1", "/update/component_tree.pb");

    List<String> files = underTest.get(PROJECT_KEY, progressWrapper);

    assertThat(files).hasSize(187);
    assertThat(files.get(0)).isEqualTo("org.sonarsource.sonarlint.intellij:sonarlint-intellij:src/main/java/org/sonarlint/intellij/ui/AbstractIssuesPanel.java");
  }

  @Test
  void should_get_empty_files_if_tree_is_empty() throws IOException {
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&ps=500&p=1", "/update/empty_component_tree.pb");

    List<String> files = underTest.get(PROJECT_KEY, progressWrapper);

    assertThat(files.size()).isZero();
  }
}
