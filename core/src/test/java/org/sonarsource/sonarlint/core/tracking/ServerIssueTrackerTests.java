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
package org.sonarsource.sonarlint.core.tracking;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ServerIssueTrackerTests {
  private final String projectKey = "dummy project";
  private final String filePath = "dummy file";
  private final ProjectBinding projectBinding = new ProjectBinding(projectKey, "", "");
  private final ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
  private final EndpointParams endpoint = mock(EndpointParams.class);
  private final ServerIssueTracker tracker = new ServerIssueTracker(mock(CachingIssueTracker.class));

  @Test
  void should_get_issues_from_engine_without_downloading() {
    ServerIssueTracker tracker = new ServerIssueTracker(mock(CachingIssueTracker.class));
    tracker.update(engine, projectBinding, Collections.singleton(filePath));
    verify(engine).getServerIssues(projectBinding, filePath);
    verifyNoMoreInteractions(engine);
  }

  @Test
  void should_download_issues_from_engine() {
    HttpClient client = MockWebServerExtension.httpClient();
    tracker.update(endpoint, client, engine, projectBinding, Collections.singleton(filePath), true);
    verify(engine).downloadServerIssues(endpoint, client, projectBinding, filePath, true, null);
    verifyNoMoreInteractions(engine);
  }

  @Test
  void should_get_issues_from_engine_if_download_failed() {
    HttpClient client = MockWebServerExtension.httpClient();
    when(engine.downloadServerIssues(endpoint, client, projectBinding, filePath, false, null)).thenThrow(new DownloadException());
    tracker.update(endpoint, client, engine, projectBinding, Collections.singleton(filePath), false);
    verify(engine).downloadServerIssues(endpoint, client, projectBinding, filePath, false, null);
    verify(engine).getServerIssues(projectBinding, filePath);
    verifyNoMoreInteractions(engine);
  }
}
