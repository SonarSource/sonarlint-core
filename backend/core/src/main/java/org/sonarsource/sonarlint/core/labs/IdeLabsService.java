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
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.labs.JoinIdeLabsProgramResponse;

public class IdeLabsService {
  public static final String PROPERTY_IDE_LABS_SUBSCRIPTION_URL = "sonarlint.internal.labs.subscription.url";
  public static final String IDE_LABS_SUBSCRIPTION_URL = "https://discover.sonarsource.com/sq-ide-labs.json";

  private final HttpClient httpClient;
  private final String subscriptionUrl;
  private final Gson gson = new Gson();

  public IdeLabsService(HttpClientProvider httpClientProvider) {
    this.httpClient = httpClientProvider.getHttpClient();
    this.subscriptionUrl = System.getProperty(PROPERTY_IDE_LABS_SUBSCRIPTION_URL, IDE_LABS_SUBSCRIPTION_URL);
  }

  public JoinIdeLabsProgramResponse joinIdeLabsProgram(String email, String ideName) {
    var requestBody = gson.toJson(new IdeLabsSubscriptionRequestPayload(email, ideName));

    try (var response = httpClient.post(subscriptionUrl, "application/json", requestBody)) {
      if (!response.isSuccessful()) {  
        return new JoinIdeLabsProgramResponse(false, "An unexpected error occurred. Server responded with status code: " + response.code());  
      }  

      var responseBody = response.bodyAsString();  
      if (gson.fromJson(responseBody, IdeLabsSubscriptionResponseBody.class).valid_email()) {  
        return new JoinIdeLabsProgramResponse(true, null);  
      }  

      return new JoinIdeLabsProgramResponse(false, "The provided email address is not valid. Please enter a valid email address.");  
    } catch (Exception e) {
      return new JoinIdeLabsProgramResponse(false, "An unexpected error occurred: " + e.getMessage());
    }
  }
}
