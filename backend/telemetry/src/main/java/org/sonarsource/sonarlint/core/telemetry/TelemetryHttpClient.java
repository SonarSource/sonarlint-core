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

import com.google.common.annotations.VisibleForTesting;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.telemetry.payload.HotspotPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.IssuePayload;
import org.sonarsource.sonarlint.core.telemetry.payload.ShareConnectedModePayload;
import org.sonarsource.sonarlint.core.telemetry.payload.ShowHotspotPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.ShowIssuePayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TaintVulnerabilitiesPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryHelpAndFeedbackPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.TelemetryRulesPayload;
import org.sonarsource.sonarlint.core.telemetry.payload.cayc.CleanAsYouCodePayload;
import org.sonarsource.sonarlint.core.telemetry.payload.cayc.NewCodeFocusPayload;

@Named
@Singleton
public class TelemetryHttpClient {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final String product;
  private final String version;
  private final String ideVersion;
  private final String platform;
  private final String architecture;
  private final HttpClient client;
  private final String endpoint;
  private final Map<String, Object> additionalAttributes;

  public TelemetryHttpClient(InitializeParams initializeParams, HttpClientProvider httpClientProvider, String telemetryEndpoint) {
    TelemetryClientConstantAttributesDto attributes = initializeParams.getTelemetryConstantAttributes();
    this.product = attributes.getProductName();
    this.version = attributes.getProductVersion();
    this.ideVersion = attributes.getIdeVersion();
    this.platform = SystemUtils.OS_NAME;
    this.architecture = SystemUtils.OS_ARCH;
    this.client = httpClientProvider.getHttpClient();
    this.endpoint = telemetryEndpoint;
    this.additionalAttributes = attributes.getAdditionalAttributes();
  }

  void upload(TelemetryLocalStorage data, TelemetryLiveAttributes telemetryLiveAttributes) {
    try {
      sendPost(createPayload(data, telemetryLiveAttributes));
    } catch (Throwable catchEmAll) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry data", catchEmAll);
      }
    }
  }

  void optOut(TelemetryLocalStorage data, TelemetryLiveAttributes telemetryLiveAttributes) {
    try {
      sendDelete(createPayload(data, telemetryLiveAttributes));
    } catch (Throwable catchEmAll) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry opt-out", catchEmAll);
      }
    }
  }

  private TelemetryPayload createPayload(TelemetryLocalStorage data, TelemetryLiveAttributes telemetryLiveAttrs) {
    var systemTime = OffsetDateTime.now();
    var daysSinceInstallation = data.installTime().until(systemTime, ChronoUnit.DAYS);
    var analyzers = TelemetryUtils.toPayload(data.analyzers());
    var notifications = TelemetryUtils.toPayload(telemetryLiveAttrs.isDevNotificationsDisabled(), data.notifications());
    var showHotspotPayload = new ShowHotspotPayload(data.showHotspotRequestsCount());
    var showIssuePayload = new ShowIssuePayload(data.getShowIssueRequestsCount());
    var hotspotPayload = new HotspotPayload(data.openHotspotInBrowserCount(), data.hotspotStatusChangedCount());
    var taintVulnerabilitiesPayload = new TaintVulnerabilitiesPayload(data.taintVulnerabilitiesInvestigatedLocallyCount(),
      data.taintVulnerabilitiesInvestigatedRemotelyCount());
    var issuePayload = new IssuePayload(data.issueStatusChangedRuleKeys(), data.issueStatusChangedCount());
    var jre = System.getProperty("java.version");
    var telemetryRulesPayload = new TelemetryRulesPayload(telemetryLiveAttrs.getNonDefaultEnabledRules(),
      telemetryLiveAttrs.getDefaultDisabledRules(), data.getRaisedIssuesRules(), data.getQuickFixesApplied());
    var helpAndFeedbackPayload = new TelemetryHelpAndFeedbackPayload(data.getHelpAndFeedbackLinkClickedCounter());
    var cleanAsYouCodePayload = new CleanAsYouCodePayload(new NewCodeFocusPayload(telemetryLiveAttrs.isSetFocusOnNewCode(), data.getCodeFocusChangedCount()));

    ShareConnectedModePayload shareConnectedModePayload;
    if (telemetryLiveAttrs.usesConnectedMode()) {
      shareConnectedModePayload = new ShareConnectedModePayload(data.getManualAddedBindingsCount(), data.getImportedAddedBindingsCount(),
        data.getAutoAddedBindingsCount(), data.getExportedConnectedModeCount());
    } else {
      shareConnectedModePayload = new ShareConnectedModePayload(null, null, null, null);
    }

    var mergedAdditionalAttributes = new HashMap<>(telemetryLiveAttrs.getAdditionalAttributes());
    mergedAdditionalAttributes.putAll(additionalAttributes);

    return new TelemetryPayload(daysSinceInstallation, data.numUseDays(), product, version, ideVersion, platform, architecture,
      telemetryLiveAttrs.usesConnectedMode(), telemetryLiveAttrs.usesSonarCloud(), systemTime, data.installTime(), platform, jre,
      telemetryLiveAttrs.getNodeVersion(), analyzers, notifications, showHotspotPayload,
      showIssuePayload, taintVulnerabilitiesPayload, telemetryRulesPayload,
      hotspotPayload, issuePayload, helpAndFeedbackPayload, cleanAsYouCodePayload, shareConnectedModePayload, mergedAdditionalAttributes);
  }

  private void sendPost(TelemetryPayload payload) {
    logTelemetryPayload(payload);
    var responseCompletableFuture = client.postAsync(endpoint, HttpClient.JSON_CONTENT_TYPE, payload.toJson());
    handleTelemetryResponse(responseCompletableFuture, "data");
  }

  private void logTelemetryPayload(TelemetryPayload payload) {
    if (isTelemetryLogEnabled()) {
      LOG.info("Sending telemetry payload.");
      LOG.info(payload.toJson());
    }
  }

  private void sendDelete(TelemetryPayload payload) {
    var responseCompletableFuture = client.deleteAsync(endpoint, HttpClient.JSON_CONTENT_TYPE, payload.toJson());
    handleTelemetryResponse(responseCompletableFuture, "opt-out");
  }

  private static void handleTelemetryResponse(CompletableFuture<HttpClient.Response> responseCompletableFuture, String uploadType) {
    responseCompletableFuture.thenAccept(response -> {
      if (!response.isSuccessful() && InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry {}: {}", uploadType, response.toString());
      }
    }).exceptionally(exception -> {
      if (InternalDebug.isEnabled()) {
        LOG.error(String.format("Failed to upload telemetry %s", uploadType), exception);
      }
      return null;
    });
  }

  @VisibleForTesting
  boolean isTelemetryLogEnabled(){
    return Boolean.parseBoolean(System.getenv("SONARLINT_TELEMETRY_LOG"));
  }
}
