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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding;


public class BindingSuggestionDto {
  private final String connectionId;
  private final String sonarProjectKey;
  private final String sonarProjectName;
  @Deprecated(forRemoval = true)
  private final boolean isFromSharedConfiguration;
  private final BindingSuggestionOrigin origin;

  public BindingSuggestionDto(String connectionId, String sonarProjectKey, String sonarProjectName, BindingSuggestionOrigin origin) {
    this.connectionId = connectionId;
    this.sonarProjectKey = sonarProjectKey;
    this.sonarProjectName = sonarProjectName;
    this.isFromSharedConfiguration = origin == BindingSuggestionOrigin.SHARED_CONFIGURATION;
    this.origin = origin;
  }


  public String getConnectionId() {
    return connectionId;
  }

  public String getSonarProjectKey() {
    return sonarProjectKey;
  }

  public String getSonarProjectName() {
    return sonarProjectName;
  }

  /**
   * @deprecated avoid calling this method if possible, since it will be removed once all the clients are migrated.
   * Rely on {@link #getOrigin()}  instead.
   */
  @Deprecated(forRemoval = true)
  public boolean isFromSharedConfiguration() {
    return isFromSharedConfiguration;
  }

  public BindingSuggestionOrigin getOrigin() {
    return origin;
  }
}
