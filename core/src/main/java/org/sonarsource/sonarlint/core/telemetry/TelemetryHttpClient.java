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

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.MediaTypes;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.telemetry.payload.ShowHotspotPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryAnalyzerPerformancePayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryNotificationsPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryPayload;
import org.sonarsource.sonarlint.core.util.ws.DeleteRequest;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;
import org.sonarsource.sonarlint.core.util.ws.PostRequest;

public class TelemetryHttpClient {

  private static final Logger LOG = Loggers.get(TelemetryHttpClient.class);

  private static final String TELEMETRY_PATH = "sonarlint";

  private final TelemetryHttpConnectorFactory httpFactory;
  private final TelemetryClientConfig clientConfig;
  private final String product;
  private final String version;
  private final String ideVersion;

  public TelemetryHttpClient(TelemetryClientConfig clientConfig, String product, String version, String ideVersion) {
    this(clientConfig, product, version, ideVersion, new TelemetryHttpConnectorFactory());
  }

  TelemetryHttpClient(TelemetryClientConfig clientConfig, String product, String version, String ideVersion, TelemetryHttpConnectorFactory httpFactory) {
    this.clientConfig = clientConfig;
    this.product = product;
    this.version = version;
    this.ideVersion = ideVersion;
    this.httpFactory = httpFactory;
  }

  void upload(TelemetryLocalStorage data, TelemetryClientAttributesProvider attributesProvider) {
    try {
      sendPost(httpFactory.buildClient(clientConfig), createPayload(data, attributesProvider));
    } catch (Throwable catchEmAll) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Failed to upload telemetry data", catchEmAll);
      }
    }
  }

  void optOut(TelemetryLocalStorage data, TelemetryClientAttributesProvider attributesProvider) {
    try {
      sendDelete(httpFactory.buildClient(clientConfig), createPayload(data, attributesProvider));
    } catch (Throwable catchEmAll) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Failed to upload telemetry opt-out", catchEmAll);
      }
    }
  }

  private TelemetryPayload createPayload(TelemetryLocalStorage data, TelemetryClientAttributesProvider attributesProvider) {
    OffsetDateTime systemTime = OffsetDateTime.now();
    long daysSinceInstallation = data.installTime().until(systemTime, ChronoUnit.DAYS);
    TelemetryAnalyzerPerformancePayload[] analyzers = TelemetryUtils.toPayload(data.analyzers());
    TelemetryNotificationsPayload notifications = TelemetryUtils.toPayload(attributesProvider.devNotificationsDisabled(), data.notifications());
    ShowHotspotPayload showHotspotPayload = new ShowHotspotPayload(data.showHotspotRequestsCount());
    String os = System.getProperty("os.name");
    String jre = System.getProperty("java.version");
    return new TelemetryPayload(daysSinceInstallation, data.numUseDays(), product, version, ideVersion,
      attributesProvider.usesConnectedMode(), attributesProvider.useSonarCloud(), systemTime, data.installTime(), os, jre, attributesProvider.nodeVersion().orElse(null),
      analyzers, notifications, showHotspotPayload);
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
