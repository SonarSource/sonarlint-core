/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.repository.connection;

import java.util.Objects;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

public class SonarCloudConnectionConfiguration extends AbstractConnectionConfiguration {

  public static String getSonarCloudUrl() {
    return System.getProperty("sonarlint.internal.sonarcloud.url", "https://squad-5-core.sc-dev.io");
  }

  private final String organization;

  public SonarCloudConnectionConfiguration(String connectionId, String organization, boolean disableNotifications) {
    super(connectionId, ConnectionKind.SONARCLOUD, disableNotifications, getSonarCloudUrl());
    this.organization = organization;
  }

  public String getOrganization() {
    return organization;
  }

  @Override
  public EndpointParams getEndpointParams() {
    return new EndpointParams(getUrl(), true, organization);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    var that = (SonarCloudConnectionConfiguration) o;
    return Objects.equals(organization, that.organization);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), organization);
  }
}
