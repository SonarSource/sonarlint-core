/*
 * SonarLint Core - Telemetry
 * Copyright (C) SonarSource Sàrl
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.telemetry.common.TelemetryUserSetting;

/**
 * Manage telemetry data and persistent storage, and stateful telemetry actions.
 * The single central point for clients to manage telemetry.
 */
public class TelemetryManager implements TelemetryUserSetting {

  static final int MIN_HOURS_BETWEEN_UPLOAD = 5;

  private final TelemetryLocalStorageManager storageManager;
  private final TelemetryHttpClient client;

  TelemetryManager(TelemetryLocalStorageManager storageManager, TelemetryHttpClient client) {
    this.storageManager = storageManager;
    this.client = client;
  }

  public void setTelemetryEnabledByUser(boolean enabled) {
    storageManager.tryUpdateAtomically(localStorage -> localStorage.setEnabled(enabled));
  }

  void uploadOnOptIn(TelemetryLiveAttributes telemetryLiveAttributes) {
    storageManager.tryUpdateAtomically(localStorage -> {
      if (localStorage.enabled() && isGracePeriodElapsedAndDayChanged(localStorage.lastUploadTime())) {
        uploadAndClearTelemetry(telemetryLiveAttributes, localStorage);
      }
    });
  }

  private static boolean isGracePeriodElapsedAndDayChanged(@Nullable LocalDateTime lastUploadTime) {
    return TelemetryUtils.isGracePeriodElapsedAndDayChanged(lastUploadTime, MIN_HOURS_BETWEEN_UPLOAD);
  }

  private void uploadAndClearTelemetry(TelemetryLiveAttributes telemetryLiveAttributes, TelemetryLocalStorage localStorage) {
    client.upload(localStorage, telemetryLiveAttributes);
    localStorage.setLastUploadTime();
    localStorage.clearAfterPing();
  }

  /**
   * Send legacy opt-out only if the user has not enabled telemetry again while attributes were fetched.
   */
  void sendOptOut(TelemetryLiveAttributes telemetryLiveAttributes) {
    storageManager.tryUpdateAtomically(data -> {
      if (!data.enabled()) {
        client.optOut(data, telemetryLiveAttributes);
      }
    });
  }

  /**
   * Upload telemetry data, when all conditions are satisfied:
   * - telemetry is enabled
   * - the day is different from the last upload
   * - the grace period has elapsed since the last upload
   * To be called periodically once a day.
   */
  void uploadAndClearTelemetry(TelemetryLiveAttributes telemetryLiveAttributes) {
    if (isTelemetryEnabledByUser() && isGracePeriodElapsedAndDayChanged(storageManager.lastUploadTime())) {
      uploadOnOptIn(telemetryLiveAttributes);
    }
  }

  public void updateTelemetry(Consumer<TelemetryLocalStorage> updater) {
    if (isTelemetryEnabledByUser()) {
      storageManager.tryUpdateAtomically(updater);
    }
  }

  @Override
  public boolean isTelemetryEnabledByUser() {
    return storageManager.isEnabled();
  }

  public OffsetDateTime installTime() {
    return storageManager.installTime();
  }
}
