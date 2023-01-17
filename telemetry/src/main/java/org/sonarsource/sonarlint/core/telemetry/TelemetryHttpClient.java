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

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.telemetry.payload.HotspotPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.ShowHotspotPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TaintVulnerabilitiesPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryRulesPayload;

public class TelemetryHttpClient {

  public static final String TELEMETRY_ENDPOINT = "https://telemetry.sonarsource.com/sonarlint";

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final String product;
  private final String version;
  private final String ideVersion;
  private final String platform;
  private final String architecture;
  private final HttpClient client;
  private final String endpoint;

  public TelemetryHttpClient(String product, String version, String ideVersion, @Nullable String platform, @Nullable String architecture, HttpClient client) {
    this(product, version, ideVersion, platform, architecture, client, TELEMETRY_ENDPOINT);
  }

  TelemetryHttpClient(String product, String version, String ideVersion, @Nullable String platform, @Nullable String architecture, HttpClient client, String endpoint) {
    this.product = product;
    this.version = version;
    this.ideVersion = ideVersion;
    this.platform = platform;
    this.architecture = architecture;
    this.client = client;
    this.endpoint = endpoint;
  }

  void upload(TelemetryLocalStorage data, TelemetryClientAttributesProvider attributesProvider) {
    try {
      sendPost(createPayload(data, attributesProvider));
    } catch (Throwable catchEmAll) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry data", catchEmAll);
      }
    }
  }

  void optOut(TelemetryLocalStorage data, TelemetryClientAttributesProvider attributesProvider) {
    try {
      sendDelete(createPayload(data, attributesProvider));
    } catch (Throwable catchEmAll) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry opt-out", catchEmAll);
      }
    }
  }

  private TelemetryPayload createPayload(TelemetryLocalStorage data, TelemetryClientAttributesProvider attributesProvider) {
    var systemTime = OffsetDateTime.now();
    var daysSinceInstallation = data.installTime().until(systemTime, ChronoUnit.DAYS);
    var analyzers = TelemetryUtils.toPayload(data.analyzers());
    var notifications = TelemetryUtils.toPayload(attributesProvider.devNotificationsDisabled(), data.notifications());
    var showHotspotPayload = new ShowHotspotPayload(data.showHotspotRequestsCount());
    var hotspotPayload = new HotspotPayload(data.openHotspotInBrowserCount());
    var taintVulnerabilitiesPayload = new TaintVulnerabilitiesPayload(data.taintVulnerabilitiesInvestigatedLocallyCount(),
      data.taintVulnerabilitiesInvestigatedRemotelyCount());
    var os = System.getProperty("os.name");
    var jre = System.getProperty("java.version");
    var telemetryRulesPayload = new TelemetryRulesPayload(attributesProvider.getNonDefaultEnabledRules(),
      attributesProvider.getDefaultDisabledRules(), data.getRaisedIssuesRules(), data.getQuickFixesApplied());
    return new TelemetryPayload(daysSinceInstallation, data.numUseDays(), product, version, ideVersion, platform, architecture,
      attributesProvider.usesConnectedMode(), attributesProvider.useSonarCloud(), systemTime, data.installTime(), os, jre, attributesProvider.nodeVersion().orElse(null),
      analyzers, notifications, showHotspotPayload, taintVulnerabilitiesPayload, telemetryRulesPayload, hotspotPayload, attributesProvider.additionalAttributes());
  }

  private void sendDelete(TelemetryPayload payload) {
    try (var response = client.delete(endpoint, HttpClient.JSON_CONTENT_TYPE, payload.toJson())) {
      if (!response.isSuccessful() && InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry opt-out: {}", response.toString());
      }
    }
  }

  private void sendPost(TelemetryPayload payload) {
    try (var response = client.post(endpoint, HttpClient.JSON_CONTENT_TYPE, payload.toJson())) {
      if (!response.isSuccessful() && InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry data: {}", response.toString());
      }
    }
  }
}
