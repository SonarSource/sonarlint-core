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
package org.sonarsource.sonarlint.core.serverapi.project;

import java.util.Optional;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Components;
import org.sonarqube.ws.Components.ShowWsResponse;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.model.DefaultRemoteProject;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class ProjectApi {
  private static final Logger LOG = Loggers.get(ProjectApi.class);
  private final ServerApiHelper helper;

  public ProjectApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Optional<ServerProject> getProject(String projectKey, ProgressMonitor monitor) {
    return fetchComponent(projectKey).map(DefaultRemoteProject::new);
  }

  public Optional<ShowWsResponse> fetchComponent(String componentKey) {
    return ServerApiHelper.processTimed(
      () -> helper.rawGet("api/components/show.protobuf?component=" + StringUtils.urlEncode(componentKey)),
      response -> {
        if (response.isSuccessful()) {
          return Optional.of(Components.ShowWsResponse.parseFrom(response.bodyAsStream()));
        }
        return Optional.empty();
      },
      duration -> LOG.debug("Downloaded project details in {}ms", duration));
  }

}
