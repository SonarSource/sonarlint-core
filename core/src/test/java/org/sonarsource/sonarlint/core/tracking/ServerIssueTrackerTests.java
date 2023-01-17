/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.tracking;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension;
import org.sonarsource.sonarlint.core.issuetracking.CachingIssueTracker;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ServerIssueTrackerTests {
  private final String projectKey = "dummy project";
  private final String filePath = "dummy file";
  private final ProjectBinding projectBinding = new ProjectBinding(projectKey, "", "");
  private final ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
  private final EndpointParams endpoint = mock(EndpointParams.class);
  private final ServerIssueTracker tracker = new ServerIssueTracker(mock(CachingIssueTracker.class), mock(CachingIssueTracker.class));

  @Test
  void should_get_issues_from_engine_without_downloading() {
    var tracker = new ServerIssueTracker(mock(CachingIssueTracker.class), mock(CachingIssueTracker.class));
    tracker.update(engine, projectBinding, "branch", Collections.singleton(filePath));
    verify(engine).getServerIssues(projectBinding, "branch", filePath);
    verify(engine).getServerHotspots(projectBinding, "branch", filePath);
    verifyNoMoreInteractions(engine);
  }

  @Test
  void should_download_issues_from_engine() {
    var client = MockWebServerExtension.httpClient();
    tracker.update(endpoint, client, engine, projectBinding, Collections.singleton(filePath), null);
    verify(engine).downloadAllServerIssuesForFile(endpoint, client, projectBinding, filePath, null, null);
    verify(engine).getServerIssues(projectBinding, null, filePath);
    verify(engine).downloadAllServerHotspotsForFile(endpoint, client, projectBinding, filePath, null, null);
    verify(engine).getServerHotspots(projectBinding, null, filePath);
    verifyNoMoreInteractions(engine);
  }

  @Test
  void should_get_issues_from_engine_if_download_failed() {
    var client = MockWebServerExtension.httpClient();
    doThrow(DownloadException.class).when(engine).downloadAllServerIssuesForFile(endpoint, client, projectBinding, filePath, null, null);
    doThrow(DownloadException.class).when(engine).downloadAllServerHotspotsForFile(endpoint, client, projectBinding, filePath, null, null);
    tracker.update(endpoint, client, engine, projectBinding, Collections.singleton(filePath), null);
    verify(engine).downloadAllServerIssuesForFile(endpoint, client, projectBinding, filePath, null, null);
    verify(engine).getServerIssues(projectBinding, null, filePath);
    verify(engine).downloadAllServerHotspotsForFile(endpoint, client, projectBinding, filePath, null, null);
    verify(engine).getServerHotspots(projectBinding, null, filePath);
    verifyNoMoreInteractions(engine);
  }
}
