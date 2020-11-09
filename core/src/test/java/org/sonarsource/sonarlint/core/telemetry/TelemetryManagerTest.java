/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.client.api.common.Language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TelemetryManagerTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private final Supplier<Boolean> usesConnectedModeSupplier = () -> true;
  private final Supplier<Boolean> usesSonarCloudSupplier = () -> true;
  private final Supplier<String> nodeVersionSupplier = () -> "10.5.2";
  private TelemetryClient client;
  private Path storagePath;
  private TelemetryManager manager;
  private TelemetryLocalStorageManager storage;

  @Before
  public void setUp() throws IOException {
    client = mock(TelemetryClient.class);
    storagePath = temp.newFile().toPath();
    storage = new TelemetryLocalStorageManager(storagePath);
    manager = new TelemetryManager(storagePath, client, usesConnectedModeSupplier, usesSonarCloudSupplier, nodeVersionSupplier);
  }

  private TelemetryManager stubbedTelemetryManager(TelemetryLocalStorage data) throws IOException {
    TelemetryLocalStorageManager storage = mock(TelemetryLocalStorageManager.class);
    when(storage.tryLoad()).thenReturn(data);
    return stubbedTelemetryManager(storage);
  }

  private TelemetryManager stubbedTelemetryManager(TelemetryLocalStorageManager storage) throws IOException {
    Path path = temp.newFile().toPath();
    return new TelemetryManager(path, client, usesConnectedModeSupplier, usesSonarCloudSupplier, nodeVersionSupplier) {
      @Override
      TelemetryLocalStorageManager newTelemetryStorage(Path ignored) {
        return storage;
      }
    };
  }

  @Test
  public void should_be_enabled_by_default() throws IOException {
    assertThat(new TelemetryManager(temp.newFile().toPath(), mock(TelemetryClient.class), mock(Supplier.class), mock(Supplier.class), mock(Supplier.class)).isEnabled()).isTrue();
  }

  @Test
  public void should_save_on_first_analysis() throws IOException {
    TelemetryLocalStorageManager storage = mockTelemetryStorage();
    TelemetryManager manager = stubbedTelemetryManager(storage);
    manager.analysisDoneOnMultipleFiles();
    verify(storage).tryUpdateAtomically(any(Consumer.class));
  }

  @Test
  public void should_increment_numDays_on_analysis_once_per_day() throws IOException {
    createAndSaveSampleData(storage);

    TelemetryLocalStorage data = storage.tryLoad();
    assertThat(data.numUseDays()).isEqualTo(5);

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnMultipleFiles();

    TelemetryLocalStorage reloaded = storage.tryLoad();
    assertThat(reloaded.numUseDays()).isEqualTo(6);

    manager.analysisDoneOnMultipleFiles();
    assertThat(reloaded.numUseDays()).isEqualTo(6);
  }

  @Test
  public void stop_should_trigger_upload_once_per_day() throws IOException {
    manager.stop();
    manager.stop();

    verify(client).upload(any(TelemetryLocalStorage.class), anyBoolean(), anyBoolean(), eq("10.5.2"));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void enable_should_trigger_upload_once_per_day() throws IOException {
    manager.enable();
    manager.enable();

    verify(client).upload(any(TelemetryLocalStorage.class), anyBoolean(), anyBoolean(), eq("10.5.2"));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void disable_should_trigger_optout() throws IOException {
    TelemetryLocalStorageManager storage = mockTelemetryStorage();
    TelemetryManager manager = stubbedTelemetryManager(storage);
    manager.disable();

    verify(client).optOut(any(TelemetryLocalStorage.class), eq(true), eq(true), eq("10.5.2"));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void uploadLazily_should_trigger_upload_once_per_day() throws IOException {
    storage.tryUpdateAtomically(d -> d.setUsedAnalysis("java", 1000));

    TelemetryLocalStorage data = storage.tryLoad();
    assertThat(data.analyzers()).isNotEmpty();
    assertThat(data.lastUploadTime()).isNull();

    manager.uploadLazily();

    TelemetryLocalStorage reloaded = storage.tryLoad();

    // should reset performance after upload
    assertThat(reloaded.analyzers()).isEmpty();

    LocalDateTime lastUploadTime = reloaded.lastUploadTime();
    assertThat(lastUploadTime).isNotNull();

    manager.uploadLazily();

    reloaded = storage.tryLoad();

    assertThat(reloaded.lastUploadTime()).isEqualTo(lastUploadTime);
    verify(client).upload(any(TelemetryLocalStorage.class), eq(true), eq(true), eq("10.5.2"));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void uploadLazily_should_trigger_upload_if_day_changed_and_hours_elapsed() throws IOException {
    createAndSaveSampleData(storage);
    manager.uploadLazily();

    TelemetryLocalStorage data = storage.tryLoad();

    LocalDateTime lastUploadTime = data.lastUploadTime()
      .minusDays(1)
      .minusHours(TelemetryManager.MIN_HOURS_BETWEEN_UPLOAD);
    storage.tryUpdateAtomically(d -> d.setLastUploadTime(lastUploadTime));

    manager.uploadLazily();

    verify(client, times(2)).upload(any(TelemetryLocalStorage.class), eq(true), eq(true), eq("10.5.2"));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void enable_should_not_wipe_out_more_recent_data() {
    TelemetryLocalStorageManager storage = new TelemetryLocalStorageManager(storagePath);
    createAndSaveSampleData(storage);

    TelemetryLocalStorage data = storage.tryLoad();
    assertThat(data.enabled()).isFalse();

    // note: the manager hasn't seen the saved data
    manager.enable();

    TelemetryLocalStorage reloaded = storage.tryLoad();
    assertThat(reloaded.enabled()).isTrue();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isAfter(data.lastUploadTime());
  }

  @Test
  public void disable_should_not_wipe_out_more_recent_data() {
    createAndSaveSampleData(storage);
    storage.tryUpdateAtomically(data -> data.setEnabled(true));

    TelemetryLocalStorage data = storage.tryLoad();
    assertThat(data.enabled()).isTrue();

    // note: the manager hasn't seen the saved data
    manager.disable();

    TelemetryLocalStorage reloaded = storage.tryLoad();
    assertThat(reloaded.enabled()).isFalse();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
  }

  @Test
  public void reporting_analysis_done_should_not_wipe_out_more_recent_data() throws IOException {
    createAndSaveSampleData(storage);

    TelemetryLocalStorage data = storage.tryLoad();

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnMultipleFiles();

    TelemetryLocalStorage reloaded = storage.tryLoad();
    assertThat(reloaded.enabled()).isEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(LocalDate.now());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays() + 1);
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).isEmpty();
  }

  @Test
  public void reporting_analysis_on_language() throws IOException {
    createAndSaveSampleData(storage);

    TelemetryLocalStorage data = storage.tryLoad();

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnSingleLanguage(Language.JAVA, 1000);

    TelemetryLocalStorage reloaded = storage.tryLoad();
    assertThat(reloaded.enabled()).isEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(LocalDate.now());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays() + 1);
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).containsKey("java");
  }

  private void createAndSaveSampleData(TelemetryLocalStorageManager storage) {
    storage.tryUpdateAtomically(data -> {
      data.setEnabled(false);
      data.setInstallTime(OffsetDateTime.now().minusDays(10));
      data.setLastUseDate(LocalDate.now().minusDays(3));
      data.setLastUploadTime(LocalDateTime.now().minusDays(2));
      data.setNumUseDays(5);
    });
  }

  private TelemetryLocalStorageManager mockTelemetryStorage() {
    TelemetryLocalStorageManager storage = mock(TelemetryLocalStorageManager.class);
    when(storage.tryLoad()).thenReturn(new TelemetryLocalStorage());
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        ((Consumer) args[0]).accept(mock(TelemetryLocalStorage.class));
        return null;
      }
    }).when(storage).tryUpdateAtomically(any(Consumer.class));
    return storage;
  }
}
