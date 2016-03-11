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
package org.sonarsource.sonarlint.core.container.connected.sync;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WsClientTestUtils {

  public static SonarLintWsClient createMockWithResponse(String url, String response) {
    SonarLintWsClient wsClient = createMock();
    return addResponse(wsClient, url, response);
  }

  private static SonarLintWsClient createMock() {
    SonarLintWsClient wsClient = mock(SonarLintWsClient.class);
    when(wsClient.getUserAgent()).thenReturn("UT");
    return wsClient;
  }

  public static SonarLintWsClient addResponse(SonarLintWsClient wsClient, String url, String response) {
    WsResponse wsResponse = mock(WsResponse.class);
    when(wsClient.get(url)).thenReturn(wsResponse);
    when(wsResponse.content())
      .thenReturn(response);
    return wsClient;
  }

  public static SonarLintWsClient createMockWithStreamResponse(String url, String resourcePath) {
    SonarLintWsClient wsClient = createMock();
    return addStreamResponse(wsClient, url, resourcePath);
  }

  public static SonarLintWsClient addStreamResponse(SonarLintWsClient wsClient, String url, String resourcePath) {
    WsResponse wsResponse = mock(WsResponse.class);
    when(wsClient.get(url)).thenReturn(wsResponse);
    when(wsResponse.contentStream()).thenReturn(Objects.requireNonNull(WsClientTestUtils.class.getResourceAsStream(resourcePath)));
    return wsClient;
  }

  public static SonarLintWsClient createMockWithReaderResponse(String url, String resourcePath) {
    SonarLintWsClient wsClient = createMock();
    return addReaderResponse(wsClient, url, resourcePath);
  }

  public static SonarLintWsClient addReaderResponse(SonarLintWsClient wsClient, String url, String resourcePath) {
    WsResponse wsResponse = mock(WsResponse.class);
    when(wsClient.get(url)).thenReturn(wsResponse);
    when(wsResponse.contentReader()).thenReturn(new InputStreamReader(Objects.requireNonNull(WsClientTestUtils.class.getResourceAsStream(resourcePath)), StandardCharsets.UTF_8));
    return wsClient;
  }

}
