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

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.Language;

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

  private static final String FOO_EVENT = "foo_event";

  private final TelemetryClientAttributesProvider attributes = new TelemetryClientAttributesProvider() {

    @Override
    public boolean usesConnectedMode() {
      return true;
    }

    @Override
    public boolean useSonarCloud() {
      return true;
    }

    @Override
    public Optional<String> nodeVersion() {
      return Optional.of("10.5.2");
    }

    @Override
    public boolean devNotificationsDisabled() {
      return true;
    }

    @Override
    public Collection<String> getNonDefaultEnabledRules() {
      return null;
    }

    @Override
    public Collection<String> getDefaultDisabledRules() {
      return null;
    }

    @Override
    public Map<String, Object> additionalAttributes() {
      return Collections.emptyMap();
    }

  };
  private TelemetryHttpClient client;
  private Path storagePath;
  private TelemetryManager manager;
  private TelemetryLocalStorageManager storage;

  @BeforeEach
  void setUp(@TempDir Path temp) throws IOException {
    client = mock(TelemetryHttpClient.class);
    storagePath = temp.resolve("storage");
    storage = new TelemetryLocalStorageManager(storagePath);
    manager = new TelemetryManager(storagePath, client, attributes);
  }

  @Test
  void should_be_enabled_by_default(@TempDir Path temp) throws IOException {
    assertThat(new TelemetryManager(temp.resolve("storage"), mock(TelemetryHttpClient.class), mock(TelemetryClientAttributesProvider.class)).isEnabled()).isTrue();
  }

  @Test
  void should_save_on_first_analysis(@TempDir Path temp) throws IOException {
    var storage = mockTelemetryStorage();
    var manager = stubbedTelemetryManager(temp, storage);
    manager.analysisDoneOnMultipleFiles();
    verify(storage).tryUpdateAtomically(any(Consumer.class));
  }

  @Test
  void should_increment_numDays_on_analysis_once_per_day() throws IOException {
    createAndSaveSampleData(storage);

    var data = storage.tryRead();
    assertThat(data.numUseDays()).isEqualTo(5);

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnMultipleFiles();

    var reloaded = storage.tryRead();
    assertThat(reloaded.numUseDays()).isEqualTo(6);

    manager.analysisDoneOnMultipleFiles();
    assertThat(reloaded.numUseDays()).isEqualTo(6);
  }

  @Test
  void stop_should_trigger_upload_once_per_day() throws IOException {
    manager.stop();
    manager.stop();

    verify(client).upload(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  void enable_should_trigger_upload_once_per_day() throws IOException {
    manager.enable();
    manager.enable();

    verify(client).upload(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  void disable_should_trigger_optout(@TempDir Path temp) throws IOException {
    var storage = mockTelemetryStorage();
    var manager = stubbedTelemetryManager(temp, storage);
    manager.disable();

    verify(client).optOut(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  void uploadLazily_should_trigger_upload_once_per_day() throws IOException {
    storage.tryUpdateAtomically(d -> d.setUsedAnalysis("java", 1000));

    var data = storage.tryRead();
    assertThat(data.analyzers()).isNotEmpty();
    assertThat(data.lastUploadTime()).isNull();

    manager.uploadLazily();

    var reloaded = storage.tryRead();

    // should reset performance after upload
    assertThat(reloaded.analyzers()).isEmpty();

    var lastUploadTime = reloaded.lastUploadTime();
    assertThat(lastUploadTime).isNotNull();

    manager.uploadLazily();

    reloaded = storage.tryRead();

    assertThat(reloaded.lastUploadTime()).isEqualTo(lastUploadTime);
    verify(client).upload(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  void uploadLazily_should_trigger_upload_if_day_changed_and_hours_elapsed() throws IOException {
    createAndSaveSampleData(storage);
    manager.uploadLazily();

    var data = storage.tryRead();

    var lastUploadTime = data.lastUploadTime()
      .minusDays(1)
      .minusHours(TelemetryManager.MIN_HOURS_BETWEEN_UPLOAD);
    storage.tryUpdateAtomically(d -> d.setLastUploadTime(lastUploadTime));

    manager.uploadLazily();

    verify(client, times(2)).upload(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  void enable_should_not_wipe_out_more_recent_data() {
    createAndSaveSampleData(storage);

    var data = storage.tryRead();
    assertThat(data.enabled()).isFalse();

    // note: the manager hasn't seen the saved data
    manager.enable();

    var reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isTrue();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isAfter(data.lastUploadTime());
  }

  @Test
  void disable_should_not_wipe_out_more_recent_data() {
    createAndSaveSampleData(storage);
    storage.tryUpdateAtomically(data -> data.setEnabled(true));

    var data = storage.tryRead();
    assertThat(data.enabled()).isTrue();

    // note: the manager hasn't seen the saved data
    manager.disable();

    var reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isFalse();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(10);
  }

  @Test
  void reporting_analysis_done_should_not_wipe_out_more_recent_data() throws IOException {
    createAndSaveSampleData(storage);

    var data = storage.tryRead();

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnMultipleFiles();

    var reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(LocalDate.now());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays() + 1);
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).isEmpty();
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(10);
  }

  @Test
  void reporting_analysis_on_language() throws IOException {
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
  void accumulate_received_dev_notifications() throws IOException {
    createAndSaveSampleData(storage);

    var data = storage.tryRead();

    // note: the manager hasn't seen the saved data
    manager.devNotificationsReceived(FOO_EVENT);
    manager.devNotificationsReceived(FOO_EVENT);
    manager.devNotificationsReceived(FOO_EVENT);

    var reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).isEmpty();
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(DEFAULT_NOTIF_COUNT + 3);
  }

  @Test
  void accumulate_clicked_dev_notifications() throws IOException {
    createAndSaveSampleData(storage);

    var data = storage.tryRead();

    // note: the manager hasn't seen the saved data
    manager.devNotificationsClicked(FOO_EVENT);
    manager.devNotificationsClicked(FOO_EVENT);

    var reloaded = storage.tryRead();

    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays() + 1);
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsClicked()).isEqualTo(DEFAULT_NOTIF_CLICKED + 2);
  }

  @Test
  void accumulate_received_open_hotspot_requests() {
    createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.showHotspotRequestReceived();
    manager.showHotspotRequestReceived();

    var reloaded = storage.tryRead();

    assertThat(reloaded.showHotspotRequestsCount()).isEqualTo(2);
  }

  @Test
  void accumulate_open_hotspot_in_browser() {
    createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.showHotspotRequestReceived();
    manager.showHotspotRequestReceived();

    var reloaded = storage.tryRead();

    assertThat(reloaded.showHotspotRequestsCount()).isEqualTo(2);
  }

  @Test
  void accumulate_investigated_taint_vulnerabilities() {
    createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.taintVulnerabilitiesInvestigatedLocally();
    manager.taintVulnerabilitiesInvestigatedLocally();
    manager.taintVulnerabilitiesInvestigatedLocally();
    manager.taintVulnerabilitiesInvestigatedRemotely();

    var reloaded = storage.tryRead();

    assertThat(reloaded.taintVulnerabilitiesInvestigatedLocallyCount()).isEqualTo(3);
    assertThat(reloaded.taintVulnerabilitiesInvestigatedRemotelyCount()).isEqualTo(1);
  }

  @Test
  void uploadLazily_should_clear_accumulated_data() {
    createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnSingleLanguage(Language.JAVA, 10);
    manager.showHotspotRequestReceived();
    manager.devNotificationsClicked(FOO_EVENT);
    manager.taintVulnerabilitiesInvestigatedLocally();
    manager.taintVulnerabilitiesInvestigatedRemotely();
    manager.addReportedRules(new HashSet<>(Arrays.asList("ruleKey1", "ruleKey2")));
    manager.addQuickFixAppliedForRule("ruleKey1");

    manager.uploadLazily();

    var reloaded = storage.tryRead();
    assertThat(reloaded.analyzers()).isEmpty();
    assertThat(reloaded.showHotspotRequestsCount()).isZero();
    assertThat(reloaded.notifications()).isEmpty();
    assertThat(reloaded.taintVulnerabilitiesInvestigatedLocallyCount()).isZero();
    assertThat(reloaded.taintVulnerabilitiesInvestigatedRemotelyCount()).isZero();
    assertThat(reloaded.getRaisedIssuesRules()).isEmpty();
    assertThat(reloaded.getQuickFixesApplied()).isEmpty();
  }

  @Test
  void accumulate_rules_activation_settings_and_reported_rules() {
    createAndSaveSampleData(storage);

    manager.addReportedRules(new HashSet<>(Arrays.asList("ruleKey1", "ruleKey1", "ruleKey2")));

    var reloaded = storage.tryRead();
    assertThat(reloaded.getRaisedIssuesRules()).hasSize(2);
    assertThat(reloaded.getRaisedIssuesRules()).contains("ruleKey1", "ruleKey2");
  }

  @Test
  void accumulate_applied_quick_fixes() {
    createAndSaveSampleData(storage);

    manager.addQuickFixAppliedForRule("ruleKey1");
    manager.addQuickFixAppliedForRule("ruleKey2");
    manager.addQuickFixAppliedForRule("ruleKey1");

    var reloaded = storage.tryRead();
    assertThat(reloaded.getQuickFixesApplied()).containsExactlyInAnyOrder("ruleKey1", "ruleKey2");
  }

  private void createAndSaveSampleData(TelemetryLocalStorageManager storage) {
    storage.tryUpdateAtomically(data -> {
      data.setEnabled(false);
      data.setInstallTime(OffsetDateTime.now().minusDays(10));
      data.setLastUseDate(LocalDate.now().minusDays(3));
      data.setLastUploadTime(LocalDateTime.now().minusDays(2));
      data.setNumUseDays(5);
      data.notifications().put(FOO_EVENT, new TelemetryNotificationsCounter(DEFAULT_NOTIF_COUNT, DEFAULT_NOTIF_CLICKED));
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

  private TelemetryManager stubbedTelemetryManager(Path path, TelemetryLocalStorageManager storage) throws IOException {
    return new TelemetryManager(path, client, attributes) {
      @Override
      TelemetryLocalStorageManager newTelemetryStorage(Path ignored) {
        return storage;
      }
    };
  }
}
