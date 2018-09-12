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
package org.sonarsource.sonarlint.core.container.connected.update;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.client.api.exceptions.ProjectNotFoundException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.exceptions.NotFoundException;
import org.sonarsource.sonarlint.core.plugin.Version;

import static org.mockito.Mockito.when;

public class ProjectQualityProfilesDownloaderTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private SonarLintWsClient wsClient = WsClientTestUtils.createMock();
  private ProjectQualityProfilesDownloader underTest = new ProjectQualityProfilesDownloader(wsClient);

  @Test
  public void not_found() {
    when(wsClient.get("/api/qualityprofiles/search.protobuf?project=key")).thenThrow(NotFoundException.class);

    exception.expect(ProjectNotFoundException.class);
    underTest.fetchModuleQualityProfiles("key",  Version.create("6.5"));
  }

  @Test
  public void not_found_before_65() {
    when(wsClient.get("/api/qualityprofiles/search.protobuf?projectKey=key")).thenThrow(NotFoundException.class);

    exception.expect(ProjectNotFoundException.class);
    underTest.fetchModuleQualityProfiles("key", Version.create("6.4"));
  }
}
