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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.http.ConnectedModeEndpoint;

public interface WsHelper {
  /**
   * Checks if it is possible to reach server with provided configuration
   */
  ValidationResult validateConnection(ConnectedModeEndpoint serverConfig);

  /**
   * Returns the list of remote organizations where the user is a member.
   */
  List<RemoteOrganization> listUserOrganizations(ConnectedModeEndpoint serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Get an organization.
   * @return null if the organization is not found
   */
  Optional<RemoteOrganization> getOrganization(ConnectedModeEndpoint serverConfig, String organizationKey, ProgressMonitor monitor);

  /**
   * Get a project.
   */
  Optional<RemoteProject> getProject(ConnectedModeEndpoint serverConfig, String projectKey, ProgressMonitor monitor);

  Optional<RemoteHotspot> getHotspot(ConnectedModeEndpoint serverConfig, GetSecurityHotspotRequestParams requestParams);
}
