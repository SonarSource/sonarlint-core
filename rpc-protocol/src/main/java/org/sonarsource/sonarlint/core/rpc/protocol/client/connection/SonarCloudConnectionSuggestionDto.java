/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.connection;

import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

public class SonarCloudConnectionSuggestionDto {

  private final String organization;
  private final String projectKey;
  private final SonarCloudRegion region;

  @Deprecated(since = "10.14")
  public SonarCloudConnectionSuggestionDto(String organization, String projectKey) {
    this(organization, projectKey, SonarCloudRegion.EU);
  }

  public SonarCloudConnectionSuggestionDto(String organization, String projectKey, SonarCloudRegion region) {
    this.organization = organization;
    this.projectKey = projectKey;
    this.region = region;
  }

  public String getOrganization() {
    return organization;
  }

  public String getProjectKey() {
    return projectKey;
  }

  public SonarCloudRegion getRegion() {
    return region;
  }
}
