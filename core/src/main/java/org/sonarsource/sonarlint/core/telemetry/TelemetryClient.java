/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.sonarqube.ws.MediaTypes;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.telemetry.TelemetryStorage;
import org.sonarsource.sonarlint.core.util.ws.DeleteRequest;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;
import org.sonarsource.sonarlint.core.util.ws.PostRequest;

import static java.time.temporal.ChronoUnit.DAYS;

import java.nio.file.Path;

public class TelemetryClient {
  private static final String TELEMETRY_PATH = "telemetry";
  private static final int MIN_HOURS_BETWEEN_UPLOAD = 5;

  private final String product;
  private final String version;
  private final TelemetryStorage telemetryStorage;
  private final Path filePath;
  private TelemetryHttpFactory httpFactory;

  TelemetryClient(String product, String version, TelemetryStorage telemetryStorage, Path filePath) {
    this(new TelemetryHttpFactory(), product, version, telemetryStorage, filePath);
  }

  TelemetryClient(TelemetryHttpFactory httpFactory, String product, String version, TelemetryStorage telemetryStorage, Path filePath) {
    this.httpFactory = httpFactory;
    this.product = product;
    this.version = version;
    this.telemetryStorage = telemetryStorage;
    this.filePath = filePath;
  }

  /**
   * Try to upload data. It will only attempt to actually upload data if {@link #shouldUpload} returns true. 
   */
  public void tryUpload(TelemetryClientConfig clientConfig, boolean connected) {
    if (shouldUpload()) {
      doUpload(clientConfig, connected);
    }
  }

  /**
   * Sends an opt-out request.
   */
  public void optOut(TelemetryClientConfig clientConfig, boolean connected) {
    doOptOut(clientConfig, connected);
  }

  public String product() {
    return product;
  }

  public String version() {
    return version;
  }

  /**
   * Checks if it should upload data, based on the last time the data was uploaded.
   * It returns false if telemetry is disabled, otherwise it returns true if:
   *   - data was never uploaded before;
   *   - data was not uploaded in the same calendar day and also not in the last 5 hours
   */
  public boolean shouldUpload() {
    if (!telemetryStorage.enabled()) {
      return false;
    }

    LocalDateTime lastUploadTime = telemetryStorage.lastUploadDateTime();
    if (lastUploadTime == null) {
      return true;
    }

    LocalDate today = LocalDate.now();
    LocalDateTime now = LocalDateTime.now();

    return !today.equals(lastUploadTime.toLocalDate()) && lastUploadTime.until(now, ChronoUnit.HOURS) >= MIN_HOURS_BETWEEN_UPLOAD;
  }

  private void doUpload(TelemetryClientConfig clientConfig, boolean connected) {
    long daysSinceInstallation = telemetryStorage.installDate().until(LocalDate.now(), DAYS);
    TelemetryPayload payload = new TelemetryPayload(daysSinceInstallation, telemetryStorage.numUseDays(), product, version, connected);
    sendPost(httpFactory.buildClient(clientConfig), payload);
    telemetryStorage.setLastUploadTime(LocalDateTime.now());
    telemetryStorage.safeSave(filePath);
  }

  private void doOptOut(TelemetryClientConfig clientConfig, boolean connected) {
    long daysSinceInstallation = telemetryStorage.installDate().until(LocalDate.now(), DAYS);
    TelemetryPayload payload = new TelemetryPayload(daysSinceInstallation, telemetryStorage.numUseDays(), product, version, connected);
    sendDelete(httpFactory.buildClient(clientConfig), payload);
    telemetryStorage.setLastUploadTime(LocalDateTime.now());
    telemetryStorage.safeSave(filePath);
  }

  private static void sendDelete(HttpConnector httpConnector, TelemetryPayload payload) {
    String json = payload.toJson();
    DeleteRequest post = new DeleteRequest(TELEMETRY_PATH);
    post.setMediaType(MediaTypes.JSON);
    httpConnector.delete(post, json).failIfNotSuccessful();
  }

  private static void sendPost(HttpConnector httpConnector, TelemetryPayload payload) {
    String json = payload.toJson();
    PostRequest post = new PostRequest(TELEMETRY_PATH);
    post.setMediaType(MediaTypes.JSON);
    httpConnector.post(post, json).failIfNotSuccessful();
  }
}
