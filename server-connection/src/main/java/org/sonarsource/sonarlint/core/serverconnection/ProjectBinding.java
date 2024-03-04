/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes the link between a project in the IDE and a project in SonarQube/SonarCloud.
 *
 */
public class ProjectBinding {
  private final String projectKey;
  private final String serverPathPrefix;
  private final String idePathPrefix;

  public ProjectBinding(String projectKey, String serverPathPrefix, String idePathPrefix) {
    this.projectKey = projectKey;
    this.serverPathPrefix = serverPathPrefix;
    this.idePathPrefix = idePathPrefix;
  }

  public String projectKey() {
    return projectKey;
  }

  public String serverPathPrefix() {
    return serverPathPrefix;
  }

  public String idePathPrefix() {
    return idePathPrefix;
  }

  public Optional<String> serverPathToIdePath(String serverPath) {
    if (!serverPath.startsWith(serverPathPrefix())) {
      return Optional.empty();
    }
    var localPrefixLen = serverPathPrefix().length();
    if (localPrefixLen > 0) {
      localPrefixLen++;
    }
    var actualLocalPrefix = idePathPrefix();
    if (!actualLocalPrefix.isEmpty()) {
      actualLocalPrefix = actualLocalPrefix + "/";
    }
    return Optional.of(actualLocalPrefix + serverPath.substring(localPrefixLen));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var that = (ProjectBinding) o;
    return Objects.equals(projectKey, that.projectKey) &&
      Objects.equals(serverPathPrefix, that.serverPathPrefix) &&
      Objects.equals(idePathPrefix, that.idePathPrefix);
  }

  @Override
  public int hashCode() {
    return Objects.hash(projectKey, serverPathPrefix, idePathPrefix);
  }
}
