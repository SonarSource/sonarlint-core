/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
import org.sonarsource.sonarlint.core.container.connected.CloseableWsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

public class AuthenticationChecker {

  private final SonarLintWsClient wsClient;

  public AuthenticationChecker(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public ValidationResult validateCredentials() {
    try (CloseableWsResponse response = wsClient.rawGet("api/authentication/validate?format=json")) {
      int code = response.code();
      if (response.isSuccessful()) {
        String responseStr = response.content();
        ValidateResponse validateResponse = new Gson().fromJson(responseStr, ValidateResponse.class);
        return new DefaultValidationResult(validateResponse.valid, validateResponse.valid ? "Authentication successful" : "Authentication failed");
      } else {
        return new DefaultValidationResult(false, "HTTP Connection failed (" + code + "): " + response.content());
      }
    }
  }

  private static class ValidateResponse {
    boolean valid;
  }

}
