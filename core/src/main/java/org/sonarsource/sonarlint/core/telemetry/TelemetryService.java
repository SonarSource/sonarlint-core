/*
 * SonarLint Core - Implementation
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
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.GetStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRule;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddReportedRulesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisDoneOnSingleLanguageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryPayloadResponse;

import static java.util.concurrent.TimeUnit.MINUTES;

@Named
@Singleton
public class TelemetryService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private static final long TELEMETRY_UPLOAD_DELAY = TimeUnit.HOURS.toMinutes(6);

  private final TelemetryLocalStorageManager telemetryLocalStorageManager;
  private final ScheduledExecutorService scheduledExecutor;
  private final TelemetryManager telemetryManager;
  private final SonarLintRpcClient client;
  private final Path userHome;

  public TelemetryService(InitializeParams initializeParams, SonarLintRpcClient sonarlintClient, HttpClientProvider httpClientProvider, @Named("userHome") Path userHome) {
    this.userHome = userHome;
    this.client = sonarlintClient;
    var telemetryInitParams = initializeParams.getClientInfo().getTelemetryInitDto();
    var storagePath = getStoragePath(telemetryInitParams.getProductKey());
    this.telemetryLocalStorageManager = new TelemetryLocalStorageManager(storagePath);
    var telemetryClient = new TelemetryHttpClient(telemetryInitParams.getProductName(), telemetryInitParams.getProductVersion(),
      telemetryInitParams.getIdeVersion(),
      telemetryInitParams.getPlatform(), telemetryInitParams.getArchitecture(), httpClientProvider.getHttpClient());

    this.telemetryManager = new TelemetryManager(storagePath, telemetryClient);
    this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "SonarLint Telemetry"));
    initTelemetryAndScheduleUpload(initializeParams);
  }

  private Path getStoragePath(String productKey) {
    return userHome.resolve("telemetry").resolve(productKey).resolve("usage");
  }

  private void initTelemetryAndScheduleUpload(InitializeParams initializeParams) {
    if (isDisabledBySystemProperty()) {
      LOG.info("Telemetry disabled by a system property");
      return;
    }
    this.telemetryLocalStorageManager.tryUpdateAtomically(storage -> storage.setInitialNewCodeFocus(initializeParams.isFocusOnNewCode()));
    var initialDelay = Integer.parseInt(System.getProperty("sonarlint.internal.telemetry.initialDelay", "1"));
    scheduledExecutor.scheduleWithFixedDelay(this::upload, initialDelay, TELEMETRY_UPLOAD_DELAY, MINUTES);
  }

  private void upload() {
    var clientTelemetryPayload = getClientTelemetryPayload();
    if (Objects.nonNull(clientTelemetryPayload)) {
      telemetryManager.uploadLazily(clientTelemetryPayload);
    }
  }

  public GetStatusResponse getStatus() {
    return new GetStatusResponse(isEnabled());
  }

  public void enableTelemetry() {
    var clientTelemetryPayload = getClientTelemetryPayload();
    if (Objects.nonNull(clientTelemetryPayload)) {
      telemetryManager.enable(clientTelemetryPayload);
    }
  }

  public void disableTelemetry() {
    var clientTelemetryPayload = getClientTelemetryPayload();
    if (Objects.nonNull(clientTelemetryPayload)) {
      telemetryManager.disable(clientTelemetryPayload);
    }
  }

  @Nullable
  private TelemetryPayloadResponse getClientTelemetryPayload() {
    try {
      return client.getTelemetryPayload().get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to fetch telemetry payload", e);
      }
    } catch (Exception e) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to fetch telemetry payload", e);
      }
    }
    return null;
  }

  private static boolean isDisabledBySystemProperty() {
    return "true".equals(System.getProperty(DISABLE_PROPERTY_KEY));
  }

  private TelemetryLocalStorageManager getTelemetryLocalStorageManager() {
    if (telemetryLocalStorageManager == null) {
      throw new IllegalStateException("Telemetry service has not been initialized");
    }
    return telemetryLocalStorageManager;
  }

  public boolean isEnabled() {
    return !isDisabledBySystemProperty() && telemetryLocalStorageManager != null && telemetryLocalStorageManager.tryRead().enabled();
  }

  public void hotspotOpenedInBrowser() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementOpenHotspotInBrowserCount);
    }
  }

  public void showHotspotRequestReceived() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementShowHotspotRequestCount);
    }
  }

  public void showIssueRequestReceived() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementShowIssueRequestCount);
    }
  }

  public void taintVulnerabilitiesInvestigatedLocally() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementTaintVulnerabilitiesInvestigatedLocallyCount);
    }
  }

  public void taintVulnerabilitiesInvestigatedRemotely() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementTaintVulnerabilitiesInvestigatedRemotelyCount);
    }
  }

  public void helpAndFeedbackLinkClicked(HelpAndFeedbackClickedParams params){
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.helpAndFeedbackLinkClicked(params.getItemId()));
    }
  }

  public void smartNotificationsReceived(String eventType) {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(s -> s.incrementDevNotificationsCount(eventType));
    }
  }

  public void analysisDoneOnSingleLanguage(AnalysisDoneOnSingleLanguageParams params) {
    if (isEnabled()){
      telemetryManager.analysisDoneOnSingleLanguage(Language.valueOf(params.getLanguage().name()), params.getAnalysisTimeMs());
    }
  }

  public void analysisDoneOnMultipleFiles() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::setUsedAnalysis);
    }
  }

  public void smartNotificationsClicked(DevNotificationsClickedParams params) {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(s -> s.incrementDevNotificationsClicked(params.getEventType()));
    }
  }

  public void addQuickFixAppliedForRule(AddQuickFixAppliedForRule params) {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(s -> s.addQuickFixAppliedForRule(params.getRuleKey()));
    }
  }

  public void addReportedRules(AddReportedRulesParams params) {
    if (isEnabled()){
      getTelemetryLocalStorageManager().tryUpdateAtomically(s -> s.addReportedRules(params.getRuleKeys()));
    }
  }

  public void hotspotStatusChanged() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementHotspotStatusChangedCount);
    }
  }

  public void issueStatusChanged(String ruleKey) {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.addIssueStatusChanged(ruleKey));
    }
  }

  public void newCodeFocusChanged() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementNewCodeFocusChange);
    }
  }

  public void stop() {
    var clientTelemetryPayload = getClientTelemetryPayload();
    if (Objects.nonNull(clientTelemetryPayload)) {
      telemetryManager.uploadLazily(clientTelemetryPayload);
    }
    stopTelemetryScheduledExecutor();
  }

  @PreDestroy
  public void close() {
    if (Objects.nonNull(scheduledExecutor)) {
      stopTelemetryScheduledExecutor();
    }
  }

  private void stopTelemetryScheduledExecutor() {
    try {
      scheduledExecutor.shutdown();
      scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to stop telemetry executor", e);
      }
    }
  }

}
