/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.client.api.common.Language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TelemetryManagerTest {
  private static final int DEFAULT_NOTIF_CLICKED = 5;
  private static final int DEFAULT_NOTIF_COUNT = 10;

  private static final String FOO_EVENT = "foo_event";

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

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

  };
  private TelemetryHttpClient client;
  private Path storagePath;
  private TelemetryManager manager;
  private TelemetryLocalStorageManager storage;

  @Before
  public void setUp() throws IOException {
    client = mock(TelemetryHttpClient.class);
    storagePath = temp.newFile().toPath();
    storage = new TelemetryLocalStorageManager(storagePath);
    manager = new TelemetryManager(storagePath, client, attributes);
  }

  private TelemetryManager stubbedTelemetryManager(TelemetryLocalStorageManager storage) throws IOException {
    Path path = temp.newFile().toPath();
    return new TelemetryManager(path, client, attributes) {
      @Override
      TelemetryLocalStorageManager newTelemetryStorage(Path ignored) {
        return storage;
      }
    };
  }

  @Test
  public void should_be_enabled_by_default() throws IOException {
    assertThat(new TelemetryManager(temp.newFile().toPath(), mock(TelemetryHttpClient.class), mock(TelemetryClientAttributesProvider.class)).isEnabled()).isTrue();
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

    TelemetryLocalStorage data = storage.tryRead();
    assertThat(data.numUseDays()).isEqualTo(5);

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnMultipleFiles();

    TelemetryLocalStorage reloaded = storage.tryRead();
    assertThat(reloaded.numUseDays()).isEqualTo(6);

    manager.analysisDoneOnMultipleFiles();
    assertThat(reloaded.numUseDays()).isEqualTo(6);
  }

  @Test
  public void stop_should_trigger_upload_once_per_day() throws IOException {
    manager.stop();
    manager.stop();

    verify(client).upload(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void enable_should_trigger_upload_once_per_day() throws IOException {
    manager.enable();
    manager.enable();

    verify(client).upload(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void disable_should_trigger_optout() throws IOException {
    TelemetryLocalStorageManager storage = mockTelemetryStorage();
    TelemetryManager manager = stubbedTelemetryManager(storage);
    manager.disable();

    verify(client).optOut(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void uploadLazily_should_trigger_upload_once_per_day() throws IOException {
    storage.tryUpdateAtomically(d -> d.setUsedAnalysis("java", 1000));

    TelemetryLocalStorage data = storage.tryRead();
    assertThat(data.analyzers()).isNotEmpty();
    assertThat(data.lastUploadTime()).isNull();

    manager.uploadLazily();

    TelemetryLocalStorage reloaded = storage.tryRead();

    // should reset performance after upload
    assertThat(reloaded.analyzers()).isEmpty();

    LocalDateTime lastUploadTime = reloaded.lastUploadTime();
    assertThat(lastUploadTime).isNotNull();

    manager.uploadLazily();

    reloaded = storage.tryRead();

    assertThat(reloaded.lastUploadTime()).isEqualTo(lastUploadTime);
    verify(client).upload(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void uploadLazily_should_trigger_upload_if_day_changed_and_hours_elapsed() throws IOException {
    createAndSaveSampleData(storage);
    manager.uploadLazily();

    TelemetryLocalStorage data = storage.tryRead();

    LocalDateTime lastUploadTime = data.lastUploadTime()
      .minusDays(1)
      .minusHours(TelemetryManager.MIN_HOURS_BETWEEN_UPLOAD);
    storage.tryUpdateAtomically(d -> d.setLastUploadTime(lastUploadTime));

    manager.uploadLazily();

    verify(client, times(2)).upload(any(TelemetryLocalStorage.class), eq(attributes));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void enable_should_not_wipe_out_more_recent_data() {
    createAndSaveSampleData(storage);

    TelemetryLocalStorage data = storage.tryRead();
    assertThat(data.enabled()).isFalse();

    // note: the manager hasn't seen the saved data
    manager.enable();

    TelemetryLocalStorage reloaded = storage.tryRead();
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

    TelemetryLocalStorage data = storage.tryRead();
    assertThat(data.enabled()).isTrue();

    // note: the manager hasn't seen the saved data
    manager.disable();

    TelemetryLocalStorage reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isFalse();
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(10);
  }

  @Test
  public void reporting_analysis_done_should_not_wipe_out_more_recent_data() throws IOException {
    createAndSaveSampleData(storage);

    TelemetryLocalStorage data = storage.tryRead();

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnMultipleFiles();

    TelemetryLocalStorage reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(LocalDate.now());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays() + 1);
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).isEmpty();
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(10);
  }

  @Test
  public void reporting_analysis_on_language() throws IOException {
    createAndSaveSampleData(storage);

    TelemetryLocalStorage data = storage.tryRead();

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnSingleLanguage(Language.JAVA, 1000);

    TelemetryLocalStorage reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(LocalDate.now());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays() + 1);
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).containsKey("java");
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(10);
  }

  @Test
  public void accumulate_received_dev_notifications() throws IOException {
    createAndSaveSampleData(storage);

    TelemetryLocalStorage data = storage.tryRead();

    // note: the manager hasn't seen the saved data
    manager.devNotificationsReceived(FOO_EVENT);
    manager.devNotificationsReceived(FOO_EVENT);
    manager.devNotificationsReceived(FOO_EVENT);

    TelemetryLocalStorage reloaded = storage.tryRead();
    assertThat(reloaded.enabled()).isEqualTo(data.enabled());
    assertThat(reloaded.installTime()).isEqualTo(data.installTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(reloaded.lastUseDate()).isEqualTo(data.lastUseDate());
    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays());
    assertThat(reloaded.lastUploadTime()).isEqualTo(data.lastUploadTime());
    assertThat(reloaded.analyzers()).isEmpty();
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsCount()).isEqualTo(DEFAULT_NOTIF_COUNT + 3);
  }

  @Test
  public void accumulate_clicked_dev_notifications() throws IOException {
    createAndSaveSampleData(storage);

    TelemetryLocalStorage data = storage.tryRead();

    // note: the manager hasn't seen the saved data
    manager.devNotificationsClicked(FOO_EVENT);
    manager.devNotificationsClicked(FOO_EVENT);

    TelemetryLocalStorage reloaded = storage.tryRead();

    assertThat(reloaded.numUseDays()).isEqualTo(data.numUseDays() + 1);
    assertThat(reloaded.notifications().get(FOO_EVENT).getDevNotificationsClicked()).isEqualTo(DEFAULT_NOTIF_CLICKED + 2);
  }

  @Test
  public void accumulate_received_open_hotspot_requests() {
    createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.showHotspotRequestReceived();
    manager.showHotspotRequestReceived();

    TelemetryLocalStorage reloaded = storage.tryRead();

    assertThat(reloaded.showHotspotRequestsCount()).isEqualTo(2);
  }

  @Test
  public void accumulate_investigated_taint_vulnerabilities() {
    createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.taintVulnerabilitiesInvestigatedLocally();
    manager.taintVulnerabilitiesInvestigatedLocally();
    manager.taintVulnerabilitiesInvestigatedLocally();
    manager.taintVulnerabilitiesInvestigatedRemotely();

    TelemetryLocalStorage reloaded = storage.tryRead();

    assertThat(reloaded.taintVulnerabilitiesInvestigatedLocallyCount()).isEqualTo(3);
    assertThat(reloaded.taintVulnerabilitiesInvestigatedRemotelyCount()).isEqualTo(1);
  }

  @Test
  public void uploadLazily_should_clear_accumulated_data() {
    createAndSaveSampleData(storage);

    // note: the manager hasn't seen the saved data
    manager.analysisDoneOnSingleLanguage(Language.JAVA, 10);
    manager.showHotspotRequestReceived();
    manager.devNotificationsClicked(FOO_EVENT);
    manager.taintVulnerabilitiesInvestigatedLocally();
    manager.taintVulnerabilitiesInvestigatedRemotely();

    manager.uploadLazily();

    TelemetryLocalStorage reloaded = storage.tryRead();
    assertThat(reloaded.analyzers()).isEmpty();
    assertThat(reloaded.showHotspotRequestsCount()).isZero();
    assertThat(reloaded.notifications()).isEmpty();
    assertThat(reloaded.taintVulnerabilitiesInvestigatedLocallyCount()).isZero();
    assertThat(reloaded.taintVulnerabilitiesInvestigatedRemotelyCount()).isZero();
  }

  @Test
  public void accumulate_rules_activation_settings_and_reported_rules() {
    createAndSaveSampleData(storage);

    manager.addDisabledRule("disabledRule1");
    manager.addDisabledRule("disabledRule2");
    manager.addEnabledRule("enabledRule1");
    manager.addEnabledRule("enabledRule2");
    manager.addEnabledRule("enabledRule3");
    manager.addReportedRule("reportedRule1");

    TelemetryLocalStorage reloaded = storage.tryRead();
    assertThat(reloaded.getExplicitlyDisabledRules()).hasSize(2);
    assertThat(reloaded.getExplicitlyEnabledRules()).hasSize(3);
    assertThat(reloaded.getReportedRules()).hasSize(1);
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
    TelemetryLocalStorageManager storage = mock(TelemetryLocalStorageManager.class);
    when(storage.tryRead()).thenReturn(new TelemetryLocalStorage());
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
