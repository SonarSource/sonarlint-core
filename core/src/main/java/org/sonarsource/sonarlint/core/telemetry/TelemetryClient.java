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

import com.google.common.annotations.VisibleForTesting;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.MediaTypes;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.util.ws.DeleteRequest;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;
import org.sonarsource.sonarlint.core.util.ws.PostRequest;

import static java.time.temporal.ChronoUnit.DAYS;

public class TelemetryClient {

  private static final Logger LOG = Loggers.get(TelemetryClient.class);

  private static final String TELEMETRY_PATH = "telemetry";

  private final TelemetryHttpFactory httpFactory;
  private final TelemetryClientConfig clientConfig;
  private final String product;
  private final String version;

  public TelemetryClient(TelemetryClientConfig clientConfig, String product, String version) {
    this(clientConfig, product, version, new TelemetryHttpFactory());
  }

  @VisibleForTesting
  TelemetryClient(TelemetryClientConfig clientConfig, String product, String version, TelemetryHttpFactory httpFactory) {
    this.clientConfig = clientConfig;
    this.product = product;
    this.version = version;
    this.httpFactory = httpFactory;
  }

  void upload(TelemetryData data) {
    try {
      sendPost(httpFactory.buildClient(clientConfig), createPayload(data));
    } catch (Exception e) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Failed to upload telemetry data", e);
      }
    }
  }

  void optOut(TelemetryData data) {
    try {
      sendDelete(httpFactory.buildClient(clientConfig), createPayload(data));
    } catch (Exception e) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Failed to upload telemetry opt-out", e);
      }
    }
  }

  private TelemetryPayload createPayload(TelemetryData data) {
    long daysSinceInstallation = data.installDate().until(LocalDate.now(), DAYS);
    OffsetDateTime systemTime = OffsetDateTime.now();
    return new TelemetryPayload(daysSinceInstallation, data.numUseDays(), product, version,
      data.usedConnectedMode(), systemTime);
  }

  private static void sendDelete(HttpConnector httpConnector, TelemetryPayload payload) {
    String json = payload.toJson();
    DeleteRequest post = new DeleteRequest(TELEMETRY_PATH);
    post.setMediaType(MediaTypes.JSON);
    httpConnector.delete(post, json).failIfNotSuccessful().close();
  }

  private static void sendPost(HttpConnector httpConnector, TelemetryPayload payload) {
    String json = payload.toJson();
    PostRequest post = new PostRequest(TELEMETRY_PATH);
    post.setMediaType(MediaTypes.JSON);
    httpConnector.post(post, json).failIfNotSuccessful().close();
  }
}
