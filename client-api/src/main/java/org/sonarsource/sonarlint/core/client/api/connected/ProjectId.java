/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ProjectId {
  private final String organizationKey;
  private final String projectKey;

  public ProjectId(@Nullable String organizationKey, String projectKey) {
    this.organizationKey = organizationKey;
    this.projectKey = projectKey;
  }

  @CheckForNull
  public String getOrganizationKey() {
    return organizationKey;
  }

  public String getProjectKey() {
    return projectKey;
  }

  @Override
  public String toString() {
    return organizationKey != null ? (organizationKey + "/" + projectKey) : projectKey;
  }

  @Override
  public int hashCode() {
    return 31 * projectKey.hashCode() + ((organizationKey == null) ? 0 : organizationKey.hashCode());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ProjectId other = (ProjectId) obj;
    return Objects.equals(other.organizationKey, organizationKey) && Objects.equals(other.projectKey, projectKey);
  }

}
