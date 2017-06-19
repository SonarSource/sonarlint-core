/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.telemetry;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.util.ws.DeleteRequest;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;
import org.sonarsource.sonarlint.core.util.ws.PostRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TelemetryClientTest {
  private TelemetryClient client;
  private HttpConnector http;

  @Before
  public void setUp() {
    http = mock(HttpConnector.class, RETURNS_DEEP_STUBS);
    TelemetryHttpFactory httpFactory = mock(TelemetryHttpFactory.class, RETURNS_DEEP_STUBS);
    when(httpFactory.buildClient(any(TelemetryClientConfig.class))).thenReturn(http);
    client = new TelemetryClient(mock(TelemetryClientConfig.class), "product", "version", httpFactory);
  }

  @Test
  public void opt_out() {
    client.optOut(new TelemetryData());
    verify(http).delete(any(DeleteRequest.class), Mockito.anyString());
  }

  @Test
  public void upload() {
    client.upload(new TelemetryData());
    verify(http).post(any(PostRequest.class), Mockito.anyString());
  }

  @Test
  public void should_not_crash_when_cannot_upload() {
    when(http.post(any(PostRequest.class), anyString())).thenThrow(new RuntimeException());
    client.upload(new TelemetryData());
  }

  @Test
  public void should_not_crash_when_cannot_opt_out() {
    when(http.delete(any(DeleteRequest.class), anyString())).thenThrow(new RuntimeException());
    client.optOut(new TelemetryData());
  }
}
