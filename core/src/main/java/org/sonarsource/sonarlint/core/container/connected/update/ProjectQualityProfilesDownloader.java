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
import java.io.InputStream;
import java.util.List;
import org.sonarqube.ws.QualityProfiles;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class ProjectQualityProfilesDownloader {

  private final SonarLintWsClient wsClient;

  public ProjectQualityProfilesDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public List<QualityProfile> fetchProjectQualityProfiles(ProjectId projectId) {
    SearchWsResponse qpResponse;
    String url = "/api/qualityprofiles/search.protobuf?projectKey=" + StringUtils.urlEncode(projectId.getProjectKey());
    String organizationKey = projectId.getOrganizationKey();
    if (organizationKey != null) {
      url += "&organization=" + StringUtils.urlEncode(organizationKey);
    }
    try (InputStream contentStream = wsClient.get(url).contentStream()) {
      qpResponse = QualityProfiles.SearchWsResponse.parseFrom(contentStream);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load module quality profiles", e);
    }
    return qpResponse.getProfilesList();
  }

}
