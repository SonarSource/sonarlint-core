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

import static org.sonarsource.sonarlint.core.telemetry.TelemetryUtils.dayChanged;

import java.nio.file.Path;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;

/**
 * Manage telemetry data, in memory and persistent storage, and stateful telemetry actions.
 * The single central point for clients to manage telemetry.
 */
public class TelemetryManager {

  public static final String TELEMETRY_ENDPOINT = "https://chestnutsl.sonarsource.com";

  static final int MIN_HOURS_BETWEEN_UPLOAD = 5;

  private final TelemetryStorage storage;
  private final TelemetryData data;
  private final TelemetryClient client;

  public TelemetryManager(Path path, TelemetryClient client) {
    this.storage = newTelemetryStorage(path);
    this.data = storage.tryLoad();
    this.client = client;
  }

  @VisibleForTesting
  TelemetryStorage newTelemetryStorage(Path path) {
    return new TelemetryStorage(path);
  }

  public boolean isEnabled() {
    return data.enabled();
  }

  public void enable() {
    tryMerge();
    data.setEnabled(true);
    saveNow();
    uploadLazily();
  }

  /**
   * Disable telemetry (opt-out).
   */
  public void disable() {
    tryMerge();
    data.setEnabled(false);
    saveNow();
    client.optOut(data);
  }

  private void tryMerge() {
    TelemetryData existing = storage.tryLoad();
    if (existing != null) {
      data.mergeFrom(existing);
    }
  }

  /**
   * Upload telemetry data, when all conditions are satisfied:
   * - the day is different from the last upload
   * - the grace period has elapsed since the last upload
   *
   * To be called periodically once a day.
   */
  public void uploadLazily() {
    if (!dayChanged(data.lastUploadTime(), MIN_HOURS_BETWEEN_UPLOAD)) {
      return;
    }

    tryMerge();

    if (!dayChanged(data.lastUploadTime(), MIN_HOURS_BETWEEN_UPLOAD)) {
      return;
    }

    data.setLastUploadTime();
    saveNow();
    client.upload(data);
    data.clearAnalyzers();
    saveNow();
  }

  public void analysisDoneOnSingleFile(@Nullable String fileExtension, int analysisTimeMs) {
    String language = TelemetryUtils.getLanguage(fileExtension);
    data.setUsedAnalysis(language, analysisTimeMs);
    mergeAndSave();
  }
  
  public void analysisDoneOnSingleLanguage(@Nullable String language, int analysisTimeMs) {
    if (language == null) {
      data.setUsedAnalysis("others", analysisTimeMs);
    } else {
      data.setUsedAnalysis(language, analysisTimeMs);
    }
    mergeAndSave();
  }

  public void analysisDoneOnMultipleFiles() {
    data.setUsedAnalysis();
    mergeAndSave();
  }

  public void usedConnectedMode(boolean hasConnectedProject, boolean isSonarCloud) {
    data.setUsedConnectedMode(hasConnectedProject);
    data.setUsedSonarcloud(hasConnectedProject && isSonarCloud);
    mergeAndSave();
  }

  /**
   * Save and upload lazily telemetry data.
   */
  public void stop() {
    saveNow();
    uploadLazily();
  }

  private void mergeAndSave() {
    tryMerge();
    saveNow();
  }

  private void saveNow() {
    storage.trySave(data);
  }
}
