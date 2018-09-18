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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.sonarqube.ws.QualityProfiles;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;
import org.sonarsource.sonarlint.core.container.connected.exceptions.NotFoundException;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.client.api.exceptions.ProjectNotFoundException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class ProjectQualityProfilesDownloader {

  private final SonarLintWsClient wsClient;

  public ProjectQualityProfilesDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public List<QualityProfile> fetchModuleQualityProfiles(String projectKey, Version serverVersion) {
    SearchWsResponse qpResponse;
    String param;
    if (serverVersion.compareToIgnoreQualifier(Version.create("6.5")) >= 0) {
      param = "project";
    } else {
      param = "projectKey";
    }
    String baseUrl = "/api/qualityprofiles/search.protobuf?" + param + "=" + StringUtils.urlEncode(projectKey);
    String organizationKey = wsClient.getOrganizationKey();
    if (organizationKey != null) {
      baseUrl += "&organization=" + StringUtils.urlEncode(organizationKey);
    }
    try (InputStream contentStream = wsClient.get(baseUrl).contentStream()) {
      qpResponse = QualityProfiles.SearchWsResponse.parseFrom(contentStream);
    } catch (NotFoundException e) {
      throw new ProjectNotFoundException(projectKey, organizationKey);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load project quality profiles", e);
    }
    return qpResponse.getProfilesList();
  }

}
