/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TelemetryManagerTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Supplier<Boolean> usesConnectedModeSupplier = () -> true;
  private Supplier<Boolean> usesSonarCloudSupplier = () -> true;
  private TelemetryClient client;
  private Path storagePath;
  private TelemetryManager manager;
  private TelemetryStorage storage;

  @Before
  public void setUp() throws IOException {
    client = mock(TelemetryClient.class);
    storagePath = temp.newFile().toPath();
    storage = new TelemetryStorage(storagePath);
    manager = new TelemetryManager(storagePath, mock(TelemetryClient.class), usesConnectedModeSupplier, usesSonarCloudSupplier);
  }

  private TelemetryManager stubbedTelemetryManager(TelemetryData data) throws IOException {
    TelemetryStorage storage = mock(TelemetryStorage.class);
    when(storage.tryLoad()).thenReturn(data);
    return stubbedTelemetryManager(storage);
  }

  private TelemetryManager stubbedTelemetryManager(TelemetryStorage storage) throws IOException {
    Path path = temp.newFile().toPath();
    return new TelemetryManager(path, client, usesConnectedModeSupplier, usesSonarCloudSupplier) {
      @Override
      TelemetryStorage newTelemetryStorage(Path ignored) {
        return storage;
      }
    };
  }

  @Test
  public void should_be_enabled_by_default() throws IOException {
    assertThat(new TelemetryManager(temp.newFile().toPath(), mock(TelemetryClient.class), mock(Supplier.class), mock(Supplier.class)).isEnabled()).isTrue();
  }

  @Test
  public void should_save_on_first_analysis() throws IOException {
    TelemetryStorage storage = mockTelemetryStorage();
    TelemetryManager manager = stubbedTelemetryManager(storage);
    manager.analysisDoneOnMultipleFiles();
    verify(storage).trySave(any(TelemetryData.class));
  }

  @Test
  public void should_increment_numDays_on_analysis_once_per_day() throws IOException {
    TelemetryData data = new TelemetryData();
    TelemetryManager manager = stubbedTelemetryManager(data);
    assertThat(data.numUseDays()).isEqualTo(0);

    manager.analysisDoneOnMultipleFiles();
    assertThat(data.numUseDays()).isEqualTo(1);

    manager.analysisDoneOnMultipleFiles();
    assertThat(data.numUseDays()).isEqualTo(1);
  }

  @Test
  public void stop_should_trigger_save_and_upload_once_per_day() throws IOException {
    TelemetryStorage storage = mockTelemetryStorage();
    TelemetryManager manager = stubbedTelemetryManager(storage);
    manager.stop();

    // once during lazy save, twice during lazy upload
    verify(storage, times(3)).trySave(any(TelemetryData.class));
    verify(client).upload(any(TelemetryData.class), anyBoolean(), anyBoolean());
  }

  @Test
  public void enable_should_trigger_upload_once_per_day() throws IOException {
    TelemetryStorage storage = mockTelemetryStorage();
    TelemetryManager manager = stubbedTelemetryManager(storage);
    manager.enable();
    manager.enable();

    verify(client).upload(any(TelemetryData.class), anyBoolean(), anyBoolean());
    verifyNoMoreInteractions(client);
  }

  @Test
  public void disable_should_trigger_optout() throws IOException {
    TelemetryStorage storage = mockTelemetryStorage();
    TelemetryManager manager = stubbedTelemetryManager(storage);
    manager.disable();

    verify(client).optOut(any(TelemetryData.class), eq(true), eq(true));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void uploadLazily_should_trigger_upload_once_per_day() throws IOException {
    TelemetryData data = new TelemetryData();
    TelemetryManager manager = stubbedTelemetryManager(data);

    data.setUsedAnalysis("java", 1000);
    assertThat(data.analyzers()).isNotEmpty();
    assertThat(data.lastUploadTime()).isNull();

    manager.uploadLazily();

    // should reset performance and usage of connected mode after upload
    assertThat(data.analyzers()).isEmpty();

    LocalDateTime lastUploadTime = data.lastUploadTime();
    assertThat(lastUploadTime).isNotNull();

    manager.uploadLazily();

    assertThat(data.lastUploadTime()).isEqualTo(lastUploadTime);
    verify(client).upload(any(TelemetryData.class), eq(true), eq(true));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void uploadLazily_should_trigger_upload_if_day_changed_and_hours_elapsed() throws IOException {
    TelemetryData data = new TelemetryData();
    TelemetryManager manager = stubbedTelemetryManager(data);
    manager.uploadLazily();

    LocalDateTime lastUploadTime = data.lastUploadTime()
      .minusDays(1)
      .minusHours(TelemetryManager.MIN_HOURS_BETWEEN_UPLOAD);
    data.setLastUploadTime(lastUploadTime);

    manager.uploadLazily();

    verify(client, times(2)).upload(any(TelemetryData.class), eq(true), eq(true));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void enable_should_not_wipe_out_more_recent_data() {
    TelemetryStorage storage = new TelemetryStorage(storagePath);
    TelemetryData data = createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.enable();

    TelemetryData reloaded = storage.tryLoad();
    assertThat(reloaded.enabled()).isNotEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isAfter(data.lastUploadTime());
  }

  @Test
  public void disable_should_not_wipe_out_more_recent_data() {
    TelemetryData data = createAndSaveSampleData(storage);
    data.setEnabled(true);
    storage.trySave(data);

    // note: the manager hasn't seen the saved data
    manager.disable();

    TelemetryData reloaded = storage.tryLoad();
    assertThat(reloaded.enabled()).isNotEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
  }

  @Test
  public void reporting_analysis_done_should_not_wipe_out_more_recent_data() throws IOException {
    TelemetryData data = createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnMultipleFiles();

    TelemetryData reloaded = storage.tryLoad();
    assertThat(reloaded.enabled()).isTrue();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(LocalDate.now());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).isEmpty();
  }

  @Test
  public void reporting_analysis_on_single_file() throws IOException {
    TelemetryData data = createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnSingleFile("java", 1000);

    TelemetryData reloaded = storage.tryLoad();
    assertThat(reloaded.enabled()).isTrue();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(LocalDate.now());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).containsKey("java");
  }

  @Test
  public void reporting_analysis_on_language() throws IOException {
    TelemetryData data = createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnSingleLanguage("java", 1000);

    TelemetryData reloaded = storage.tryLoad();
    assertThat(reloaded.enabled()).isTrue();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(LocalDate.now());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).containsKey("java");
  }

  private TelemetryData createAndSaveSampleData(TelemetryStorage storage) {
    TelemetryData data = new TelemetryData();
    data.setEnabled(false);
    data.setInstallTime(OffsetDateTime.now().minusDays(10));
    data.setLastUseDate(LocalDate.now().minusDays(3));
    data.setLastUploadTime(LocalDateTime.now().minusDays(2));
    data.setNumUseDays(5);

    storage.trySave(data);
    return data;
  }

  private TelemetryStorage mockTelemetryStorage() {
    TelemetryStorage storage = mock(TelemetryStorage.class);
    when(storage.tryLoad()).thenReturn(new TelemetryData());
    return storage;
  }
}
