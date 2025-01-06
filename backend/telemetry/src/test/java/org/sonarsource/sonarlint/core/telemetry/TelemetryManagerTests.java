/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
  private TelemetryManager telemetryManager;
  private TelemetryLocalStorageManager storageManager;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    client = mock(TelemetryHttpClient.class);
    storageManager = new TelemetryLocalStorageManager(temp.resolve("storage"), mock(InitializeParams.class));
    telemetryManager = new TelemetryManager(storageManager, client);
  }

  @Test
  void enable_should_trigger_upload_once_per_day() {
    var telemetryPayload = getTelemetryLiveAttributesDto();

    telemetryManager.enable(telemetryPayload);
    telemetryManager.enable(telemetryPayload);

    verify(client).upload(any(TelemetryLocalStorage.class), eq(telemetryPayload));
    verifyNoMoreInteractions(client);
  }

  @Test
  void disable_should_trigger_optout() {
    var mockStorageManager = mockTelemetryStorage();
    var manager = new TelemetryManager(mockStorageManager, client);
    var telemetryPayload = getTelemetryLiveAttributesDto();

    manager.disable(telemetryPayload);

    verify(client).optOut(any(TelemetryLocalStorage.class), eq(telemetryPayload));
    verifyNoMoreInteractions(client);
  }

  @Test
  void uploadAndClearTelemetry_should_trigger_upload_once_per_day() {
    var telemetryPayload = getTelemetryLiveAttributesDto();

    storageManager.tryUpdateAtomically(d -> d.setUsedAnalysis("java", 1000));

    var data = storageManager.tryRead();
    assertThat(data.analyzers()).isNotEmpty();
    assertThat(data.lastUploadTime()).isNull();

    telemetryManager.uploadAndClearTelemetry(telemetryPayload);

    var reloaded = storageManager.tryRead();

    // should reset performance after upload
    assertThat(reloaded.analyzers()).isEmpty();

    var lastUploadTime = reloaded.lastUploadTime();
    assertThat(lastUploadTime).isNotNull();

    telemetryManager.uploadAndClearTelemetry(telemetryPayload);

    reloaded = storageManager.tryRead();

    assertThat(reloaded.lastUploadTime()).isEqualTo(lastUploadTime);
    verify(client).upload(any(TelemetryLocalStorage.class), eq(telemetryPayload));
    verifyNoMoreInteractions(client);
  }

  @Test
  void uploadAndClearTelemetry_should_trigger_upload_if_day_changed_and_hours_elapsed() {
    var telemetryPayload = getTelemetryLiveAttributesDto();

    createAndSaveSampleData(storageManager);
    storageManager.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.setEnabled(true));
    telemetryManager.uploadAndClearTelemetry(telemetryPayload);

    var data = storageManager.tryRead();

    var lastUploadTime = data.lastUploadTime()
      .minusDays(1)
      .minusHours(TelemetryManager.MIN_HOURS_BETWEEN_UPLOAD);
    storageManager.tryUpdateAtomically(d -> d.setLastUploadTime(lastUploadTime));

    telemetryManager.uploadAndClearTelemetry(telemetryPayload);

    verify(client, times(2)).upload(any(TelemetryLocalStorage.class), eq(telemetryPayload));
    verifyNoMoreInteractions(client);
  }

  @Test
  void uploadAndClearTelemetry_should_not_trigger_upload_if_telemetry_disabled_by_user() {
    createAndSaveSampleData(storageManager);
    TelemetryLiveAttributes telemetryLiveAttributesDto = getTelemetryLiveAttributesDto();

    telemetryManager.uploadAndClearTelemetry(telemetryLiveAttributesDto);

    assertThat(storageManager.isEnabled()).isFalse();
    verify(client, never()).upload(any(TelemetryLocalStorage.class), eq(telemetryLiveAttributesDto));
    verifyNoMoreInteractions(client);
  }

  @Test
  void updateTelemetry_should_not_trigger_update_if_telemetry_disabled_by_user() {
    createAndSaveSampleData(storageManager);

    telemetryManager.updateTelemetry(telemetryLocalStorage -> telemetryLocalStorage.setNumUseDays(10));

    TelemetryLocalStorage localStorage = storageManager.tryRead();
    assertThat(localStorage.enabled()).isFalse();
    assertThat(localStorage.numUseDays()).isEqualTo(5);
  }

  @Test
  void enable_should_not_wipe_out_more_recent_data() {
    var telemetryLiveAttributes = getTelemetryLiveAttributesDto();

    createAndSaveSampleData(storageManager);

    var data = storageManager.tryRead();
    assertThat(data.enabled()).isFalse();

    // note: the manager hasn't seen the saved data
    telemetryManager.enable(telemetryLiveAttributes);

    var reloaded = storageManager.tryRead();
    assertThat(reloaded.enabled()).isTrue();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
  }

  @Test
  void disable_should_not_wipe_out_more_recent_data() {
    var telemetryPayload = getTelemetryLiveAttributesDto();

    createAndSaveSampleData(storageManager);
    storageManager.tryUpdateAtomically(data -> data.setEnabled(true));

    var data = storageManager.tryRead();
    assertThat(data.enabled()).isTrue();

    // note: the manager hasn't seen the saved data
    telemetryManager.disable(telemetryPayload);

    var reloaded = storageManager.tryRead();
    assertThat(reloaded.enabled()).isFalse();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime());
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(10);
  }

  @Test
  void uploadAndClearTelemetry_should_clear_accumulated_data() {
    var telemetryPayload = getTelemetryLiveAttributesDto();

    createAndSaveSampleData(storageManager);
    storageManager.tryUpdateAtomically(data -> {
      data.setEnabled(true);
      data.setUsedAnalysis("java", 1000);
      data.incrementHotspotStatusChangedCount();
      data.incrementOpenHotspotInBrowserCount();
      data.incrementShowHotspotRequestCount();
      data.incrementShowIssueRequestCount();
      data.fixSuggestionReceived("suggestionId", AiSuggestionSource.SONARCLOUD, 2);
      data.fixSuggestionResolved("suggestionId", FixSuggestionStatus.ACCEPTED, 0);
      data.incrementTaintVulnerabilitiesInvestigatedLocallyCount();
      data.incrementTaintVulnerabilitiesInvestigatedRemotelyCount();
      data.setLastUploadTime(LocalDateTime.now().minusDays(2));
      data.setNumUseDays(5);
      data.notifications().put(FOO_EVENT, new TelemetryNotificationsCounter(DEFAULT_NOTIF_COUNT, DEFAULT_NOTIF_CLICKED));
      data.getHelpAndFeedbackLinkClickedCounter().put(SUGGEST_FEATURE, new TelemetryHelpAndFeedbackCounter(DEFAULT_HELP_AND_FEEDBACK_COUNT));
    });

    telemetryManager.uploadAndClearTelemetry(telemetryPayload);

    var reloaded = storageManager.tryRead();
    assertThat(reloaded.analyzers()).isEmpty();
    assertThat(reloaded.showHotspotRequestsCount()).isZero();
    assertThat(reloaded.notifications()).isEmpty();
    assertThat(reloaded.taintVulnerabilitiesInvestigatedLocallyCount()).isZero();
    assertThat(reloaded.taintVulnerabilitiesInvestigatedRemotelyCount()).isZero();
    assertThat(reloaded.hotspotStatusChangedCount()).isZero();
    assertThat(reloaded.getShowIssueRequestsCount()).isZero();
    assertThat(reloaded.getFixSuggestionReceivedCounter()).isEmpty();
    assertThat(reloaded.getFixSuggestionResolved()).isEmpty();
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
    doAnswer((Answer<Void>) invocation -> {
      var args = invocation.getArguments();
      ((Consumer) args[0]).accept(mock(TelemetryLocalStorage.class));
      return null;
    }).when(storage).tryUpdateAtomically(any(Consumer.class));
    return storage;
  }

  private static TelemetryLiveAttributes getTelemetryLiveAttributesDto() {
    var serverAttributes = new TelemetryServerAttributes(true, true, false, Collections.emptyList(), Collections.emptyList(), "3.1.7");
    var clientAttributes = new TelemetryClientLiveAttributesResponse(emptyMap());
    return new TelemetryLiveAttributes(serverAttributes, clientAttributes);
  }
}
