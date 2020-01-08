/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mockito;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
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

  @Rule
  public final EnvironmentVariables env = new EnvironmentVariables();

  @Rule
  public LogTester logTester = new LogTester();

  @Before
  public void setUp() {
    http = mock(HttpConnector.class, RETURNS_DEEP_STUBS);
    TelemetryHttpFactory httpFactory = mock(TelemetryHttpFactory.class, RETURNS_DEEP_STUBS);
    when(httpFactory.buildClient(any(TelemetryClientConfig.class))).thenReturn(http);
    client = new TelemetryClient(mock(TelemetryClientConfig.class), "product", "version", "ideversion", httpFactory);
  }

  @AfterClass
  public static void after() {
    // to avoid conflicts with SonarLintLogging
    new LogTester().setLevel(LoggerLevel.TRACE);
  }

  @Test
  public void opt_out() {
    client.optOut(new TelemetryData(), true, true);
    verify(http).delete(any(DeleteRequest.class), Mockito.anyString());
  }

  @Test
  public void upload() {
    client.upload(new TelemetryData(), true, true);
    verify(http).post(any(PostRequest.class), Mockito.anyString());
  }

  @Test
  public void should_not_crash_when_cannot_upload() {
    when(http.post(any(PostRequest.class), anyString())).thenThrow(new RuntimeException());
    client.upload(new TelemetryData(), true, true);
  }

  @Test
  public void should_not_crash_when_cannot_opt_out() {
    when(http.delete(any(DeleteRequest.class), anyString())).thenThrow(new RuntimeException());
    client.optOut(new TelemetryData(), true, true);
  }

  @Test
  public void failed_upload_should_log_if_debug() {
    env.set("SONARLINT_INTERNAL_DEBUG", "true");
    when(http.post(any(PostRequest.class), anyString())).thenThrow(new IllegalStateException("msg"));
    client.upload(new TelemetryData(), true, true);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed to upload telemetry data");
  }

  @Test
  public void failed_optout_should_log_if_debug() {
    env.set("SONARLINT_INTERNAL_DEBUG", "true");
    when(http.delete(any(DeleteRequest.class), anyString())).thenThrow(new IllegalStateException("msg"));
    client.optOut(new TelemetryData(), true, true);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed to upload telemetry opt-out");
  }
}
