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

import java.util.List;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Qualityprofiles;
import org.sonarqube.ws.Qualityprofiles.SearchWsResponse;
import org.sonarqube.ws.Qualityprofiles.SearchWsResponse.QualityProfile;
import org.sonarsource.sonarlint.core.client.api.exceptions.ProjectNotFoundException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.exceptions.NotFoundException;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class ProjectQualityProfilesDownloader {

  private static final Logger LOG = Loggers.get(ProjectQualityProfilesDownloader.class);

  private final SonarLintWsClient wsClient;

  public ProjectQualityProfilesDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public List<QualityProfile> fetchModuleQualityProfiles(String projectKey) {
    SearchWsResponse qpResponse;
    StringBuilder url = new StringBuilder();
    url.append("/api/qualityprofiles/search.protobuf?project=");
    url.append(StringUtils.urlEncode(projectKey));
    wsClient.getOrganizationKey()
      .ifPresent(org -> url.append("&organization=").append(StringUtils.urlEncode(org)));
    try {
      qpResponse = SonarLintWsClient.processTimed(
        () -> wsClient.get(url.toString()),
        response -> Qualityprofiles.SearchWsResponse.parseFrom(response.bodyAsStream()),
        duration -> LOG.debug("Downloaded project quality profiles in {}ms", duration));
    } catch (NotFoundException e) {
      throw new ProjectNotFoundException(projectKey, wsClient.getOrganizationKey().orElse(null));
    }
    return qpResponse.getProfilesList();
  }

}
