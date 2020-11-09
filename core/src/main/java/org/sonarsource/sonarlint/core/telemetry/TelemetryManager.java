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

import java.nio.file.Path;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.Language;

import static org.sonarsource.sonarlint.core.telemetry.TelemetryUtils.dayChanged;

/**
 * Manage telemetry data and persistent storage, and stateful telemetry actions.
 * The single central point for clients to manage telemetry.
 */
public class TelemetryManager {

  public static final String TELEMETRY_ENDPOINT = "https://telemetry.sonarsource.com";

  static final int MIN_HOURS_BETWEEN_UPLOAD = 5;

  private final TelemetryLocalStorageManager storage;
  private final TelemetryHttpClient client;
  private final TelemetryClientAttributesProvider attributesProvider;

  public TelemetryManager(Path path, TelemetryHttpClient client, TelemetryClientAttributesProvider attributesProvider) {
    this.storage = newTelemetryStorage(path);
    this.attributesProvider = attributesProvider;
    this.client = client;
  }

  TelemetryLocalStorageManager newTelemetryStorage(Path path) {
    return new TelemetryLocalStorageManager(path);
  }

  public boolean isEnabled() {
    return storage.tryLoad().enabled();
  }

  public void enable() {
    storage.tryUpdateAtomically(data -> {
      data.setEnabled(true);
    });
    uploadLazily();
  }

  /**
   * Disable telemetry (opt-out).
   */
  public void disable() {
    storage.tryUpdateAtomically(data -> {
      data.setEnabled(false);
      client.optOut(data, attributesProvider);
    });
  }

  /**
   * Upload telemetry data, when all conditions are satisfied:
   * - the day is different from the last upload
   * - the grace period has elapsed since the last upload
   * To be called periodically once a day.
   */
  public void uploadLazily() {
    TelemetryLocalStorage readData = storage.tryLoad();
    if (!dayChanged(readData.lastUploadTime(), MIN_HOURS_BETWEEN_UPLOAD)) {
      return;
    }

    storage.tryUpdateAtomically(data -> {
      client.upload(data, attributesProvider);
      data.setLastUploadTime();
      data.clearAnalyzers();
    });
  }

  public void analysisDoneOnSingleLanguage(@Nullable Language language, int analysisTimeMs) {
    storage.tryUpdateAtomically(data -> {
      if (language == null) {
        data.setUsedAnalysis("others", analysisTimeMs);
      } else {
        data.setUsedAnalysis(language.getLanguageKey(), analysisTimeMs);
      }
    });
  }

  public void analysisDoneOnMultipleFiles() {
    storage.tryUpdateAtomically(TelemetryLocalStorage::setUsedAnalysis);
  }

  /**
   * Save and upload lazily telemetry data.
   */
  public void stop() {
    uploadLazily();
  }

}
