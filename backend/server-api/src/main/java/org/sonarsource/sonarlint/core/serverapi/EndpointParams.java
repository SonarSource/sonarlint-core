/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi;

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * SonarQube or SonarCloud endpoint parameters
 */
public class EndpointParams {

  private final String baseUrl;
  private final boolean sonarCloud;
  @Nullable
  private final String organization;

  public EndpointParams(String baseUrl, boolean isSonarCloud, @Nullable String organization) {
    this.baseUrl = baseUrl;
    this.sonarCloud = isSonarCloud;
    this.organization = organization;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public boolean isSonarCloud() {
    return sonarCloud;
  }

  /**
   * Organization can be missing even for SonarCloud, because some API calls are made before knowing the organization (like fetching user organizations)
   */
  public Optional<String> getOrganization() {
    return sonarCloud ? Optional.ofNullable(organization) : Optional.empty();
  }

}
