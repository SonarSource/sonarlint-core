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

import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;
import org.sonarsource.sonarlint.core.util.ws.PostRequest;

import static java.time.temporal.ChronoUnit.DAYS;

import java.nio.file.Path;

public class TelemetryClient {
  private static final String TELEMETRY_ENDPOINT = "https://chestnutsl.sonarsource.com";
  private static final String TELEMETRY_PATH = "telemetry";
  private static final int TELEMETRY_TIMEOUT = 30_000;
  private static final int MIN_HOURS_BETWEEN_UPLOAD = 5;

  private final String product;
  private final String version;
  private final TelemetryStorage telemetryStorage;
  private final Path filePath;

  TelemetryClient(String product, String version, TelemetryStorage telemetryStorage, Path filePath) {
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
    postTelemetryData(clientConfig, payload);
    telemetryStorage.setLastUploadTime(LocalDateTime.now());
    telemetryStorage.safeSave(filePath);
  }

  private static void postTelemetryData(TelemetryClientConfig clientConfig, TelemetryPayload payload) {
    String json = payload.toJson();

    HttpConnector httpConnector = buildTelemetryClient(clientConfig);
    PostRequest post = new PostRequest(TELEMETRY_PATH);
    post.setMediaType("application/json");
    httpConnector.post(post, json).failIfNotSuccessful();
  }

  private static HttpConnector buildTelemetryClient(TelemetryClientConfig clientConfig) {
    return HttpConnector.newBuilder().url(TELEMETRY_ENDPOINT)
      .userAgent(clientConfig.userAgent())
      .proxy(clientConfig.proxy())
      .proxyCredentials(clientConfig.proxyLogin(), clientConfig.proxyPassword())
      .readTimeoutMilliseconds(TELEMETRY_TIMEOUT)
      .connectTimeoutMilliseconds(TELEMETRY_TIMEOUT)
      .setSSLSocketFactory(clientConfig.sslSocketFactory())
      .setTrustManager(clientConfig.trustManager())
      .build();
  }
}
