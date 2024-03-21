/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.function.Consumer;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Manage telemetry data and persistent storage, and stateful telemetry actions.
 * The single central point for clients to manage telemetry.
 */
@Named
@Singleton
public class TelemetryManager {

  static final int MIN_HOURS_BETWEEN_UPLOAD = 5;

  private final TelemetryLocalStorageManager storageManager;
  private final TelemetryHttpClient client;

  TelemetryManager(TelemetryLocalStorageManager storageManager, TelemetryHttpClient client) {
    this.storageManager = storageManager;
    this.client = client;
  }

  void enable(TelemetryLiveAttributes telemetryLiveAttributes) {
    storageManager.tryUpdateAtomically(localStorage -> {
      localStorage.setEnabled(true);
      if (isGracePeriodElapsedAndDayChanged(localStorage.lastUploadTime())) {
        uploadAndClearTelemetry(telemetryLiveAttributes, localStorage);
      }
    });
  }

  private static boolean isGracePeriodElapsedAndDayChanged(LocalDateTime lastUploadTime) {
    return TelemetryUtils.isGracePeriodElapsedAndDayChanged(lastUploadTime, MIN_HOURS_BETWEEN_UPLOAD);
  }

  private void uploadAndClearTelemetry(TelemetryLiveAttributes telemetryLiveAttributes, TelemetryLocalStorage localStorage) {
    client.upload(localStorage, telemetryLiveAttributes);
    localStorage.setLastUploadTime();
    localStorage.clearAfterPing();
  }

  /**
   * Disable telemetry (opt-out).
   */
  void disable(TelemetryLiveAttributes telemetryLiveAttributes) {
    storageManager.tryUpdateAtomically(data -> {
      data.setEnabled(false);
      client.optOut(data, telemetryLiveAttributes);
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
      storageManager.tryUpdateAtomically(localStorage -> uploadAndClearTelemetry(telemetryLiveAttributes, localStorage));
    }
  }

  public void updateTelemetry(Consumer<TelemetryLocalStorage> updater) {
    if (isTelemetryEnabledByUser()) {
      storageManager.tryUpdateAtomically(updater);
    }
  }

  public boolean isTelemetryEnabledByUser() {
    return storageManager.isEnabled();
  }
}
