/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2019 SonarSource SA
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
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;

public interface WsHelper {
  /**
   * Checks if it is possible to reach server with provided configuration
   * @since 2.1
   */
  ValidationResult validateConnection(ServerConfiguration serverConfig);

  /**
   * Create authentication token with the provided server configuration
   * @since 2.1
   * @param force Whether it should revoke any existing token with the same name, or fail if it exists.
   * @throws UnsupportedServerException if SonarQube server version &lt; 5.3
   * @throws IllegalStateException for other errors, for example if server is not ready or if a token with the given name already exists 
   * and force is not set to true
   */
  String generateAuthenticationToken(ServerConfiguration serverConfig, String name, boolean force);

  /**
   * Returns the list of remote organizations
   */
  List<RemoteOrganization> listOrganizations(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Returns the list of remote organizations where the user is a member.
   * @since 3.5
   */
  List<RemoteOrganization> listUserOrganizations(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Get an organization.
   * @returns null if the organization is not found
   * @since 3.5
   */
  Optional<RemoteOrganization> getOrganization(ServerConfiguration serverConfig, String organizationKey, ProgressMonitor monitor);

  /**
   * Get a project.
   * @since 4.0
   */
  Optional<RemoteProject> getProject(ServerConfiguration serverConfig, String projectKey, ProgressMonitor monitor);
}
