/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.net.URI;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.commons.lang3.Strings;
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

public class SonarCloudConnectionConfiguration extends AbstractConnectionConfiguration {

  private final URI apiUri;
  private final String organization;
  private final SonarCloudRegion region;

  public SonarCloudConnectionConfiguration(URI uri, URI apiUri, String connectionId, String organization, SonarCloudRegion region, boolean disableNotifications) {
    super(connectionId, ConnectionKind.SONARCLOUD, disableNotifications, uri.toString());
    this.apiUri = apiUri;
    this.organization = organization;
    this.region = region;
  }

  public String getOrganization() {
    return organization;
  }

  @Override
  public EndpointParams getEndpointParams() {
    return new EndpointParams(getUrl(), Strings.CS.removeEnd(apiUri.toString(), "/"), true, organization);
  }

  public SonarCloudRegion getRegion() {
    return region;
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
