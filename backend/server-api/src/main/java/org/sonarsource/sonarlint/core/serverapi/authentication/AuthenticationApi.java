/*
 * SonarLint Core - Server API
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverapi.authentication;

import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.system.ValidationResult;

public class AuthenticationApi {

  private final ServerApiHelper serverApiHelper;

  public AuthenticationApi(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  public ValidationResult validate(SonarLintCancelMonitor cancelMonitor) {
    var validateResponse = serverApiHelper.getJson("api/authentication/validate?format=json", ValidateResponseDto.class, cancelMonitor);
    return new ValidationResult(validateResponse.valid(), validateResponse.valid() ? "Authentication successful" : "Authentication failed");
  }

}
