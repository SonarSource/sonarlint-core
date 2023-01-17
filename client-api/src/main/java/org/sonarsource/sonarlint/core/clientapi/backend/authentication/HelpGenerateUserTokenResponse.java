/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.authentication;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * For older SQ servers or SC, automatic token generation is not supported. In this case a null token will be returned.
 */
public class HelpGenerateUserTokenResponse {
  private final String token;

  public HelpGenerateUserTokenResponse(@Nullable String token) {
    this.token = token;
  }

  @CheckForNull
  public String getToken() {
    return token;
  }
}
