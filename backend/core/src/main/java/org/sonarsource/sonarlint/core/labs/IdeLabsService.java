/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.labs;

import com.google.gson.Gson;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.labs.JoinIdeLabsProgramResponse;

public class IdeLabsService {
  private final IdeLabsHttpClient labsHttpClient;
  private final Gson gson = new Gson();

  public IdeLabsService(IdeLabsHttpClient labsHttpClient) {
    this.labsHttpClient = labsHttpClient;
  }

  public JoinIdeLabsProgramResponse joinIdeLabsProgram(String email, String ideName) {
    try (var response = labsHttpClient.join(email, ideName)) {
      if (!response.isSuccessful()) {  
        return new JoinIdeLabsProgramResponse(false, "An unexpected error occurred. Server responded with status code: " + response.code());  
      }  

      var responseBody = response.bodyAsString();  
      if (gson.fromJson(responseBody, IdeLabsSubscriptionResponseBody.class).validEmail()) {
        return new JoinIdeLabsProgramResponse(true, null);  
      }  

      return new JoinIdeLabsProgramResponse(false, "The provided email address is not valid. Please enter a valid email address.");  
    } catch (Exception e) {
      return new JoinIdeLabsProgramResponse(false, "An unexpected error occurred: " + e.getMessage());
    }
  }
}
