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

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class TelemetryManagerTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private TelemetryClient client;
  private TelemetryStorage storage;
  private TelemetryData data;

  private TelemetryManager newTelemetryManager() throws IOException {
    storage = mock(TelemetryStorage.class);
    data = new TelemetryData();
    when(storage.tryLoad()).thenReturn(data);

    Path path = temp.newFile().toPath();
    client = mock(TelemetryClient.class);

    TelemetryManager manager = new TelemetryManager(path, client) {
      @Override
      TelemetryStorage newTelemetryStorage(Path path) {
        return storage;
      }
    };
    return manager;
  }

  @Test
  public void should_save_on_first_analysis() throws IOException {
    TelemetryManager manager = newTelemetryManager();
    manager.usedAnalysis();
    verify(storage).trySave(any(TelemetryData.class));
  }

  @Test
  public void should_not_save_twice_on_same_day() throws IOException {
    TelemetryManager manager = newTelemetryManager();
    manager.usedAnalysis();
    manager.usedAnalysis();
    manager.usedAnalysis();

    // load once to initialize, and one more time to save
    verify(storage, times(2)).tryLoad();
    verify(storage).trySave(any(TelemetryData.class));
    verifyNoMoreInteractions(storage);
  }

  @Test
  public void should_increment_numDays_on_analysis_once_per_day() throws IOException {
    TelemetryManager manager = newTelemetryManager();
    assertThat(data.numUseDays()).isEqualTo(0);

    manager.usedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    manager.usedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);
  }

  @Test
  public void usedConnectedMode_should_trigger_save_once_per_day() throws IOException {
    TelemetryManager manager = newTelemetryManager();
    manager.usedConnectedMode(true);
    verify(storage).trySave(any(TelemetryData.class));
  }

  @Test
  public void stop_should_trigger_save_and_upload_once_per_day() throws IOException {
    TelemetryManager manager = newTelemetryManager();
    manager.stop();

    // once during lazy save, once during lazy upload
    verify(storage, times(2)).trySave(any(TelemetryData.class));
    verify(client).upload(any(TelemetryData.class));
  }

  @Test
  public void enable_should_trigger_upload_once_per_day() throws IOException {
    TelemetryManager manager = newTelemetryManager();
    manager.enable();
    manager.enable();

    verify(client).upload(any(TelemetryData.class));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void disable_should_trigger_optout() throws IOException {
    TelemetryManager manager = newTelemetryManager();
    manager.disable();

    verify(client).optOut(any(TelemetryData.class));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void uploadLazily_should_trigger_upload_once_per_day() throws IOException {
    TelemetryManager manager = newTelemetryManager();

    assertThat(data.lastUploadTime()).isNull();
    manager.uploadLazily();

    LocalDateTime lastUploadTime = data.lastUploadTime();
    assertThat(lastUploadTime).isNotNull();

    manager.uploadLazily();
    assertThat(data.lastUploadTime()).isEqualTo(lastUploadTime);

    verify(client).upload(any(TelemetryData.class));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void uploadLazily_should_trigger_upload_if_day_changed_and_hours_elapsed() throws IOException {
    TelemetryManager manager = newTelemetryManager();
    manager.uploadLazily();

    LocalDateTime lastUploadTime = data.lastUploadTime()
        .minusDays(1)
        .minusHours(TelemetryManager.MIN_HOURS_BETWEEN_UPLOAD);
    data.setLastUploadTime(lastUploadTime);

    manager.uploadLazily();

    verify(client, times(2)).upload(any(TelemetryData.class));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void save_should_not_wipe_out_existing_newer_data() {

  }
  // TODO test that existing data is not wiped out on each action
}
