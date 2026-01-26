/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.labs;

import com.google.gson.Gson;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.springframework.beans.factory.annotation.Qualifier;

public class IdeLabsHttpClient {
  private final HttpClient httpClient;
  private final String labsSubscriptionEndpoint;

  private final Gson gson = new Gson();

  public IdeLabsHttpClient(HttpClientProvider httpClientProvider, @Qualifier("labsSubscriptionEndpoint") String labsSubscriptionEndpoint) {
    this.httpClient = httpClientProvider.getHttpClientWithoutAuth();
    this.labsSubscriptionEndpoint = labsSubscriptionEndpoint;
  }

  public HttpClient.Response join(String email, String ideName) {
    var requestBody = gson.toJson(new IdeLabsSubscriptionRequestPayload(email, ideName));

    return httpClient.post(labsSubscriptionEndpoint, "application/json", requestBody);
  }
}
