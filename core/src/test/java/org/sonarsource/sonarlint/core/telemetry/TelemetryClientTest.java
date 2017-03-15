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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.util.ws.DeleteRequest;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;
import org.sonarsource.sonarlint.core.util.ws.PostRequest;

public class TelemetryClientTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private TelemetryClient client;
  private TelemetryStorage storage;
  private Path file;
  private TelemetryHttpFactory httpFactory;
  private HttpConnector http;

  @Before
  public void setUp() {
    http = mock(HttpConnector.class, RETURNS_DEEP_STUBS);
    httpFactory = mock(TelemetryHttpFactory.class, RETURNS_DEEP_STUBS);
    when(httpFactory.buildClient(Mockito.any(TelemetryClientConfig.class))).thenReturn(http);
    storage = new TelemetryStorage();
    file = temp.getRoot().toPath().resolve("file");
    client = new TelemetryClient(httpFactory, "product", "version", storage, file);
  }

  @Test
  public void getters() {
    assertThat(client.version()).isEqualTo("version");
    assertThat(client.product()).isEqualTo("product");
  }

  @Test
  public void opt_out() {
    client.optOut(mock(TelemetryClientConfig.class), true);
    verify(http).delete(Mockito.any(DeleteRequest.class), Mockito.anyString());
  }

  @Test
  public void upload() {
    client.tryUpload(mock(TelemetryClientConfig.class), true);
    verify(http).post(Mockito.any(PostRequest.class), Mockito.anyString());
  }

  @Test
  public void should_upload() {
    LocalDateTime fourHoursAgo = LocalDateTime.now().minusHours(4);
    storage.setLastUploadTime(fourHoursAgo);
    assertThat(client.shouldUpload()).isFalse();

    LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
    storage.setLastUploadTime(oneDayAgo);
    assertThat(client.shouldUpload()).isTrue();

    storage.setEnabled(false);
    assertThat(client.shouldUpload()).isFalse();
  }

}
