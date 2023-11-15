/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryPayloadResponse;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TelemetryManagerTests {
  private static final int DEFAULT_NOTIF_CLICKED = 5;
  private static final int DEFAULT_NOTIF_COUNT = 10;
  private static final int DEFAULT_HELP_AND_FEEDBACK_COUNT = 12;

  private static final String FOO_EVENT = "foo_event";
  private static final String SUGGEST_FEATURE = "suggestFeature";

  private TelemetryHttpClient client;
  private Path storagePath;
  private TelemetryManager manager;
  private TelemetryLocalStorageManager storage;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    client = mock(TelemetryHttpClient.class);
    storagePath = temp.resolve("storage");
    storage = new TelemetryLocalStorageManager(storagePath);
    manager = new TelemetryManager(storagePath, client);
  }

  @Test
  void enable_should_trigger_upload_once_per_day() {
    var telemetryPayload = new TelemetryPayloadResponse(true, false, null, false, emptyList(), emptyList(), emptyMap());

    manager.enable(telemetryPayload);
    manager.enable(telemetryPayload);

    verify(client).upload(any(TelemetryLocalStorage.class), eq(telemetryPayload));
    verifyNoMoreInteractions(client);
  }

  @Test
  void disable_should_trigger_optout(@TempDir Path temp) {
    var storage = mockTelemetryStorage();
    var manager = stubbedTelemetryManager(temp, storage);
    var telemetryPayload = new TelemetryPayloadResponse(true, false, null, false, emptyList(), emptyList(), emptyMap());

    manager.disable(telemetryPayload);

    verify(client).optOut(any(TelemetryLocalStorage.class), eq(telemetryPayload));
    verifyNoMoreInteractions(client);
  }

  @Test
  void uploadLazily_should_trigger_upload_once_per_day() {
    var telemetryPayload = new TelemetryPayloadResponse(true, false, null, false, emptyList(), emptyList(), emptyMap());

    storage.tryUpdateAtomically(d -> d.setUsedAnalysis("java", 1000));

    var data = storage.tryRead();
    assertThat(data.analyzers()).isNotEmpty();
    assertThat(data.lastUploadTime()).isNull();

    manager.uploadLazily(telemetryPayload);

    var reloaded = storage.tryRead();

    // should reset performance after upload
    assertThat(reloaded.analyzers()).isEmpty();

    var lastUploadTime = reloaded.lastUploadTime();
    assertThat(lastUploadTime).isNotNull();

    manager.uploadLazily(telemetryPayload);

    reloaded = storage.tryRead();

    assertThat(reloaded.lastUploadTime()).isEqualTo(lastUploadTime);
    verify(client).upload(any(TelemetryLocalStorage.class), eq(telemetryPayload));
    verifyNoMoreInteractions(client);
  }

  @Test
  void uploadLazily_should_trigger_upload_if_day_changed_and_hours_elapsed() {
    var telemetryPayload = new TelemetryPayloadResponse(true, false, null, false, emptyList(), emptyList(), emptyMap());

    createAndSaveSampleData(storage);
    manager.uploadLazily(telemetryPayload);

    var data = storage.tryRead();

    var lastUploadTime = data.lastUploadTime()
      .minusDays(1)
      .minusHours(TelemetryManager.MIN_HOURS_BETWEEN_UPLOAD);
    storage.tryUpdateAtomically(d -> d.setLastUploadTime(lastUploadTime));

    manager.uploadLazily(telemetryPayload);

    verify(client, times(2)).upload(any(TelemetryLocalStorage.class), eq(telemetryPayload));
    verifyNoMoreInteractions(client);
  }

  @Test
  void enable_should_not_wipe_out_more_recent_data() {
    var telemetryPayload = new TelemetryPayloadResponse(true, false, null, false, emptyList(), emptyList(), emptyMap());

    createAndSaveSampleData(storage);

    var data = storage.tryRead();
    assertThat(data.enabled()).isFalse();

    // note: the manager hasn't seen the saved data
    manager.enable(telemetryPayload);

    var reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isTrue();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isAfter(data.lastUploadTime());
  }

  @Test
  void disable_should_not_wipe_out_more_recent_data() {
    var telemetryPayload = new TelemetryPayloadResponse(true, false, null, false, emptyList(), emptyList(), emptyMap());

    createAndSaveSampleData(storage);
    storage.tryUpdateAtomically(data -> data.setEnabled(true));

    var data = storage.tryRead();
    assertThat(data.enabled()).isTrue();

    // note: the manager hasn't seen the saved data
    manager.disable(telemetryPayload);

    var reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isFalse();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(10);
  }

  @Test
  void reporting_analysis_on_language() {
    createAndSaveSampleData(storage);

    var data = storage.tryRead();

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnSingleLanguage(Language.JAVA, 1000);

    var reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(LocalDate.now());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays() + 1);
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).containsKey("java");
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(10);
  }

  @Test
  void uploadLazily_should_clear_accumulated_data() {
    var telemetryPayload = new TelemetryPayloadResponse(true, false, null, false, emptyList(), emptyList(), emptyMap());

    createAndSaveSampleData(storage);
    storage.tryUpdateAtomically(data -> {
      data.setEnabled(true);
      data.setUsedAnalysis("java", 1000);
      data.incrementHotspotStatusChangedCount();
      data.incrementOpenHotspotInBrowserCount();
      data.incrementShowHotspotRequestCount();
      data.incrementShowIssueRequestCount();
      data.incrementTaintVulnerabilitiesInvestigatedLocallyCount();
      data.incrementTaintVulnerabilitiesInvestigatedRemotelyCount();
      data.setLastUploadTime(LocalDateTime.now().minusDays(2));
      data.setNumUseDays(5);
      data.notifications().put(FOO_EVENT, new TelemetryNotificationsCounter(DEFAULT_NOTIF_COUNT, DEFAULT_NOTIF_CLICKED));
      data.getHelpAndFeedbackLinkClickedCounter().put(SUGGEST_FEATURE, new TelemetryHelpAndFeedbackCounter(DEFAULT_HELP_AND_FEEDBACK_COUNT));
    });

    manager.uploadLazily(telemetryPayload);

    var reloaded = storage.tryRead();
    assertThat(reloaded.analyzers()).isEmpty();
    assertThat(reloaded.showHotspotRequestsCount()).isZero();
    assertThat(reloaded.notifications()).isEmpty();
    assertThat(reloaded.taintVulnerabilitiesInvestigatedLocallyCount()).isZero();
    assertThat(reloaded.taintVulnerabilitiesInvestigatedRemotelyCount()).isZero();
    assertThat(reloaded.hotspotStatusChangedCount()).isZero();
    assertThat(reloaded.getShowIssueRequestsCount()).isZero();
    assertThat(reloaded.openHotspotInBrowserCount()).isZero();
    assertThat(reloaded.getHelpAndFeedbackLinkClickedCounter()).isEmpty();
  }

  private void createAndSaveSampleData(TelemetryLocalStorageManager storage) {
    storage.tryUpdateAtomically(data -> {
      data.setEnabled(false);
      data.setInstallTime(OffsetDateTime.now().minusDays(10));
      data.setLastUseDate(LocalDate.now().minusDays(3));
      data.setLastUploadTime(LocalDateTime.now().minusDays(2));
      data.setNumUseDays(5);
      data.notifications().put(FOO_EVENT, new TelemetryNotificationsCounter(DEFAULT_NOTIF_COUNT, DEFAULT_NOTIF_CLICKED));
      data.getHelpAndFeedbackLinkClickedCounter().put(SUGGEST_FEATURE, new TelemetryHelpAndFeedbackCounter(DEFAULT_HELP_AND_FEEDBACK_COUNT));
    });
  }

  private TelemetryLocalStorageManager mockTelemetryStorage() {
    var storage = mock(TelemetryLocalStorageManager.class);
    when(storage.tryRead()).thenReturn(new TelemetryLocalStorage());
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        var args = invocation.getArguments();
        ((Consumer) args[0]).accept(mock(TelemetryLocalStorage.class));
        return null;
      }
    }).when(storage).tryUpdateAtomically(any(Consumer.class));
    return storage;
  }

  private TelemetryManager stubbedTelemetryManager(Path path, TelemetryLocalStorageManager storage) {
    return new TelemetryManager(path, client) {
      @Override
      TelemetryLocalStorageManager newTelemetryStorage(Path ignored) {
        return storage;
      }
    };
  }
}
