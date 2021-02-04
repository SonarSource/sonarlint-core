/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.validate;

import com.google.gson.Gson;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class AuthenticationChecker {

  private final ServerApiHelper wsClient;

  public AuthenticationChecker(ServerApiHelper wsClient) {
    this.wsClient = wsClient;
  }

  public ValidationResult validateCredentials() {
    try (HttpClient.Response response = wsClient.rawGet("api/authentication/validate?format=json")) {
      int code = response.code();
      if (response.isSuccessful()) {
        String responseStr = response.bodyAsString();
        ValidateResponse validateResponse = new Gson().fromJson(responseStr, ValidateResponse.class);
        return new DefaultValidationResult(validateResponse.valid, validateResponse.valid ? "Authentication successful" : "Authentication failed");
      } else {
        return new DefaultValidationResult(false, "HTTP Connection failed (" + code + "): " + response.bodyAsString());
      }
    }
  }

  private static class ValidateResponse {
    boolean valid;
  }

}
