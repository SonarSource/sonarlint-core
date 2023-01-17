/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerApiProviderTests {


  private final ConnectionConfigurationRepository connectionRepository = mock(ConnectionConfigurationRepository.class);
  private final SonarLintClient client = mock(SonarLintClient.class);
  private final ServerApiProvider underTest = new ServerApiProvider(connectionRepository, client);

  @Test
  void getServerApi_for_sonarqube() {
    var endpointParams = mock(EndpointParams.class);
    when(connectionRepository.getEndpointParams("sq1")).thenReturn(Optional.of(endpointParams));
    var httpClient = mock(HttpClient.class);
    when(client.getHttpClient("sq1")).thenReturn(httpClient);

    var serverApi = underTest.getServerApi("sq1");

    assertThat(serverApi).isPresent();
  }

  @Test
  void getServerApi_for_sonarcloud() {
    var endpointParams = mock(EndpointParams.class);
    when(connectionRepository.getEndpointParams("sc1")).thenReturn(Optional.of(endpointParams));
    var httpClient = mock(HttpClient.class);
    when(client.getHttpClient("sc1")).thenReturn(httpClient);

    var serverApi = underTest.getServerApi("sc1");

    assertThat(serverApi).isPresent();
  }

  @Test
  void getServerApi_returns_empty_if_connection_doesnt_exists() {
    when(connectionRepository.getConnectionById("sc1")).thenReturn(null);
    var httpClient = mock(HttpClient.class);
    when(client.getHttpClient("sc1")).thenReturn(httpClient);

    var serverApi = underTest.getServerApi("sc1");

    assertThat(serverApi).isEmpty();
  }

  @Test
  void getServerApi_returns_empty_if_client_cant_provide_httpclient() {
    when(connectionRepository.getConnectionById("sc1")).thenReturn(new SonarCloudConnectionConfiguration("sc1", "myorg"));
    when(client.getHttpClient("sc1")).thenReturn(null);

    var serverApi = underTest.getServerApi("sc1");

    assertThat(serverApi).isEmpty();
  }
}