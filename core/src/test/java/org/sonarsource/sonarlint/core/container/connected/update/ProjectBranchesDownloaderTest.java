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

import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.MockWebServerExtension;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectBranchesDownloaderTest {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private final static String PROJECT_KEY = "project1";

  private ProjectBranchesDownloader underTest;

  @BeforeEach
  public void setUp() {
    underTest = new ProjectBranchesDownloader(mockServer.serverApiHelper());
  }

  @Test
  void shouldDownloadBranches() {
    mockServer.addStringResponse("/api/project_branches/list?project=project1",
      "{\n" +
        "  \"branches\": [\n" +
        "    {\n" +
        "      \"name\": \"feature/foo\",\n" +
        "      \"isMain\": false,\n" +
        "      \"type\": \"BRANCH\",\n" +
        "      \"status\": {\n" +
        "        \"qualityGateStatus\": \"OK\"\n" +
        "      },\n" +
        "      \"analysisDate\": \"2017-04-03T13:37:00+0100\",\n" +
        "      \"excludedFromPurge\": false\n" +
        "    },\n" +
        "    {\n" +
        "      \"name\": \"master\",\n" +
        "      \"isMain\": true,\n" +
        "      \"type\": \"BRANCH\",\n" +
        "      \"status\": {\n" +
        "        \"qualityGateStatus\": \"ERROR\"\n" +
        "      },\n" +
        "      \"analysisDate\": \"2017-04-01T01:15:42+0100\",\n" +
        "      \"excludedFromPurge\": true\n" +
        "    }\n" +
        "  ]\n" +
        "}");

    var branches = underTest.getBranches(PROJECT_KEY);

    assertThat(branches).hasSize(2);
  }

  @Test
  void returnEmptyListOnMalformedResponse() {
    mockServer.addStringResponse("/api/project_branches/list?project=project1",
      "{\n" +
        "  \"branches\": [\n" +
        "    { }" +
        "  ]\n" +
        "}");

    var branches = underTest.getBranches(PROJECT_KEY);

    assertThat(branches).isEmpty();
  }

}
