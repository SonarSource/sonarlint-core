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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import javax.annotation.Nullable;

import static org.apache.commons.lang.StringUtils.removeEnd;

public class SonarQubeConnectionConfiguration extends AbstractConnectionConfiguration {

  private final String serverUrl;

  public SonarQubeConnectionConfiguration(String connectionId, String serverUrl) {
    super(connectionId);
    this.serverUrl = serverUrl;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public boolean isSameServerUrl(String otherUrl) {
    URI myUri;
    URI otherUri;
    try {
      myUri = new URI(removeEnd(serverUrl, "/"));
      otherUri = new URI(removeEnd(otherUrl, "/"));
    } catch (URISyntaxException e) {
      return false;
    }
    return Objects.equals(myUri, otherUri);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    var that = (SonarQubeConnectionConfiguration) o;
    return Objects.equals(serverUrl, that.serverUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), serverUrl);
  }
}
