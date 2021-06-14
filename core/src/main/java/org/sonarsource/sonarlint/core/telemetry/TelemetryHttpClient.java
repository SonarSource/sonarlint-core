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
import java.util.Collection;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.telemetry.payload.ShowHotspotPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TaintVulnerabilitiesPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryAnalyzerPerformancePayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryNotificationsPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryRulesPayload;

public class TelemetryHttpClient {

  public static final String TELEMETRY_ENDPOINT = "https://telemetry.sonarsource.com/sonarlint";

  private static final Logger LOG = Loggers.get(TelemetryHttpClient.class);

  private final String product;
  private final String version;
  private final String ideVersion;
  private final HttpClient client;
  private final String endpoint;

  public TelemetryHttpClient(String product, String version, String ideVersion, HttpClient client) {
    this(product, version, ideVersion, client, TELEMETRY_ENDPOINT);
  }

  TelemetryHttpClient(String product, String version, String ideVersion, HttpClient client, String endpoint) {
    this.product = product;
    this.version = version;
    this.ideVersion = ideVersion;
    this.client = client;
    this.endpoint = endpoint;
  }

  void upload(TelemetryLocalStorage data, TelemetryClientAttributesProvider attributesProvider) {
    try {
      sendPost(createPayload(data, attributesProvider));
    } catch (Throwable catchEmAll) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Failed to upload telemetry data", catchEmAll);
      }
    }
  }

  void optOut(TelemetryLocalStorage data, TelemetryClientAttributesProvider attributesProvider) {
    try {
      sendDelete(createPayload(data, attributesProvider));
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
    TaintVulnerabilitiesPayload taintVulnerabilitiesPayload = new TaintVulnerabilitiesPayload(data.taintVulnerabilitiesInvestigatedLocallyCount(),
      data.taintVulnerabilitiesInvestigatedRemotelyCount());
    String os = System.getProperty("os.name");
    String jre = System.getProperty("java.version");
    TelemetryRulesPayload telemetryRulesPayload = new TelemetryRulesPayload(attributesProvider.getExplicitlyEnabledRules(),
      attributesProvider.getExplicitlyDisabledRules(), data.getReportedRules());
    return new TelemetryPayload(daysSinceInstallation, data.numUseDays(), product, version, ideVersion,
      attributesProvider.usesConnectedMode(), attributesProvider.useSonarCloud(), systemTime, data.installTime(), os, jre, attributesProvider.nodeVersion().orElse(null),
      analyzers, notifications, showHotspotPayload, taintVulnerabilitiesPayload, telemetryRulesPayload);
  }

  private void sendDelete(TelemetryPayload payload) {
    try (HttpClient.Response response = client.delete(endpoint, HttpClient.JSON_CONTENT_TYPE, payload.toJson())) {
      if (!response.isSuccessful() && SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Failed to upload telemetry opt-out: {}", response.toString());
      }
    }
  }

  private void sendPost(TelemetryPayload payload) {
    try (HttpClient.Response response = client.post(endpoint, HttpClient.JSON_CONTENT_TYPE, payload.toJson())) {
      if (!response.isSuccessful() && SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Failed to upload telemetry data: {}", response.toString());
      }
    }
  }
}
