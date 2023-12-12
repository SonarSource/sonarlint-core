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
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Language;

import static org.sonarsource.sonarlint.core.telemetry.TelemetryUtils.dayChanged;

/**
 * Manage telemetry data and persistent storage, and stateful telemetry actions.
 * The single central point for clients to manage telemetry.
 */
public class TelemetryManager {

  static final int MIN_HOURS_BETWEEN_UPLOAD = 5;

  private final TelemetryLocalStorageManager storage;
  private final TelemetryHttpClient client;

  TelemetryManager(Path path, TelemetryHttpClient client) {
    this.storage = newTelemetryStorage(path);
    this.client = client;
  }

  TelemetryLocalStorageManager newTelemetryStorage(Path path) {
    return new TelemetryLocalStorageManager(path);
  }

  void enable(TelemetryLiveAttributes telemetryLiveAttributes) {
    storage.tryUpdateAtomically(data -> data.setEnabled(true));
    uploadLazily(telemetryLiveAttributes);
  }

  /**
   * Disable telemetry (opt-out).
   */
  void disable(TelemetryLiveAttributes telemetryLiveAttributes) {
    storage.tryUpdateAtomically(data -> {
      data.setEnabled(false);
      client.optOut(data, telemetryLiveAttributes);
    });
  }

  /**
   * Upload telemetry data, when all conditions are satisfied:
   * - the day is different from the last upload
   * - the grace period has elapsed since the last upload
   * To be called periodically once a day.
   */
  void uploadLazily(TelemetryLiveAttributes telemetryLiveAttributes) {
    var readData = storage.tryRead();
    if (!dayChanged(readData.lastUploadTime(), MIN_HOURS_BETWEEN_UPLOAD)) {
      return;
    }

    storage.tryUpdateAtomically(data -> {
      client.upload(data, telemetryLiveAttributes);
      data.setLastUploadTime();
      data.clearAfterPing();
    });
  }

  void analysisDoneOnSingleLanguage(@Nullable Language language, int analysisTimeMs) {
    storage.tryUpdateAtomically(data -> {
      if (language == null) {
        data.setUsedAnalysis("others", analysisTimeMs);
      } else {
        data.setUsedAnalysis(language.getLanguageKey(), analysisTimeMs);
      }
    });
  }
}
