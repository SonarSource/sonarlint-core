/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.users;

import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class UsersApi {
  private final ServerApiHelper helper;

  public UsersApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  /**
   * Fetch the current user info using api/users/current.
   * Returns null if the response cannot be parsed or if the id field is not present.
   * Note: The id field is available on SonarQube Cloud and SonarQube Server 2025.6+.
   */
  @CheckForNull
  public String getCurrentUserId(SonarLintCancelMonitor cancelMonitor) {
    var userResponse = helper.getJson("/api/users/current", CurrentUserResponseDto.class, cancelMonitor);
    return userResponse == null ? null : userResponse.id();
  }
}
