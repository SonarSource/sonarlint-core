/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2018 SonarSource SA
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

public class ProjectBinding {
  private final String projectKey;
  private final String sqPathPrefix;
  private final String localPathPrefix;

  public ProjectBinding(String projectKey, String sqPathPrefix, String localPathPrefix) {
    this.projectKey = projectKey;
    this.sqPathPrefix = sqPathPrefix;
    this.localPathPrefix = localPathPrefix;
  }

  public String projectKey() {
    return projectKey;
  }

  public String sqPathPrefix() {
    return sqPathPrefix;
  }

  public String localPathPrefix() {
    return localPathPrefix;
  }
}
