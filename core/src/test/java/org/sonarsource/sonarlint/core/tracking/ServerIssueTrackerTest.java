/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ServerIssueTrackerTest {
  @Test
  public void should_get_issues_from_engine_without_downloading() {
    String moduleKey = "dummy module";
    String filePath = "dummy file";
    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);

    ServerIssueTracker tracker = new ServerIssueTracker(mock(Logger.class), mock(CachingIssueTracker.class));
    tracker.update(engine, moduleKey, Collections.singleton(filePath));
    verify(engine).getServerIssues(moduleKey, filePath);
    verifyNoMoreInteractions(engine);
  }

  @Test
  public void should_download_issues_from_engine() {
    String moduleKey = "dummy module";
    String filePath = "dummy file";
    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
    ServerConfiguration serverConfiguration = mock(ServerConfiguration.class);

    ServerIssueTracker tracker = new ServerIssueTracker(mock(Logger.class), mock(CachingIssueTracker.class));
    tracker.update(serverConfiguration, engine, moduleKey, Collections.singleton(filePath));
    verify(engine).downloadServerIssues(serverConfiguration, moduleKey, filePath);
    verifyNoMoreInteractions(engine);
  }

  @Test
  public void should_get_issues_from_engine_if_download_failed() {
    String moduleKey = "dummy module";
    String filePath = "dummy file";
    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
    ServerConfiguration serverConfiguration = mock(ServerConfiguration.class);

    ServerIssueTracker tracker = new ServerIssueTracker(mock(Logger.class), mock(CachingIssueTracker.class));
    when(engine.downloadServerIssues(serverConfiguration, moduleKey, filePath)).thenThrow(new DownloadException());
    tracker.update(serverConfiguration, engine, moduleKey, Collections.singleton(filePath));
    verify(engine).downloadServerIssues(serverConfiguration, moduleKey, filePath);
    verify(engine).getServerIssues(moduleKey, filePath);
    verifyNoMoreInteractions(engine);
  }
}
