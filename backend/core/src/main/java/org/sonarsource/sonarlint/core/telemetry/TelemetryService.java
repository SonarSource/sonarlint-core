/*
 * SonarLint Core - Implementation
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

import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.LocalOnlyIssueStatusChangedEvent;
import org.sonarsource.sonarlint.core.event.ServerIssueStatusChangedEvent;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.GetStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.springframework.context.event.EventListener;

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
  private final TelemetryServerAttributesProvider telemetryServerAttributesProvider;
  private final SonarLintRpcClient client;
  private final Path userHome;

  public TelemetryService(InitializeParams initializeParams, SonarLintRpcClient sonarlintClient, HttpClientProvider httpClientProvider,
    TelemetryServerAttributesProvider telemetryServerAttributesProvider, @Named("userHome") Path userHome) {
    this.userHome = userHome;
    this.client = sonarlintClient;
    this.telemetryServerAttributesProvider = telemetryServerAttributesProvider;
    var telemetryInitParams = initializeParams.getTelemetryConstantAttributes();
    var storagePath = getStoragePath(telemetryInitParams.getProductKey());
    this.telemetryLocalStorageManager = new TelemetryLocalStorageManager(storagePath);
    var telemetryServerConstantAttributes = telemetryServerAttributesProvider.getTelemetryServerConstantAttributes();
    var telemetryClient = new TelemetryHttpClient(telemetryInitParams.getProductName(), telemetryInitParams.getProductVersion(),
      telemetryInitParams.getIdeVersion(), telemetryServerConstantAttributes.getPlatform(), telemetryServerConstantAttributes.getArchitecture(),
      httpClientProvider.getHttpClient(), telemetryInitParams.getAdditionalAttributes());

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
    var telemetryLiveAttributes = getTelemetryLiveAttributes();
    if (Objects.nonNull(telemetryLiveAttributes)) {
      telemetryManager.uploadLazily(telemetryLiveAttributes);
    }
  }

  public GetStatusResponse getStatus() {
    return new GetStatusResponse(isEnabled());
  }

  public void enableTelemetry() {
    var telemetryLiveAttributes = getTelemetryLiveAttributes();
    if (Objects.nonNull(telemetryLiveAttributes)) {
      telemetryManager.enable(telemetryLiveAttributes);
    }
  }

  public void disableTelemetry() {
    var telemetryLiveAttributes = getTelemetryLiveAttributes();
    if (Objects.nonNull(telemetryLiveAttributes)) {
      telemetryManager.disable(telemetryLiveAttributes);
    }
  }

  @Nullable
  private TelemetryLiveAttributes getTelemetryLiveAttributes() {
    try {
      var serverLiveAttributes = telemetryServerAttributesProvider.getTelemetryServerLiveAttributes();
      var clientLiveAttributes = client.getTelemetryLiveAttributes().get(10, TimeUnit.SECONDS);
      return new TelemetryLiveAttributes(serverLiveAttributes, clientLiveAttributes);
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

  public void analysisDoneOnSingleLanguage(@Nullable org.sonarsource.sonarlint.core.rpc.protocol.common.Language language, int analysisTimeMs) {
    if (isEnabled()){
      var coreLanguage = Objects.nonNull(language) ? SonarLanguage.valueOf(language.name()) : null;
      telemetryManager.analysisDoneOnSingleLanguage(coreLanguage, analysisTimeMs);
    }
  }

  public void analysisDoneOnMultipleFiles() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::setUsedAnalysis);
    }
  }

  public void smartNotificationsClicked(String eventType) {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(s -> s.incrementDevNotificationsClicked(eventType));
    }
  }

  public void addQuickFixAppliedForRule(String ruleKey) {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(s -> s.addQuickFixAppliedForRule(ruleKey));
    }
  }

  public void addReportedRules(Set<String> ruleKeys) {
    if (isEnabled()){
      getTelemetryLocalStorageManager().tryUpdateAtomically(s -> s.addReportedRules(ruleKeys));
    }
  }

  public void hotspotStatusChanged() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementHotspotStatusChangedCount);
    }
  }

  @EventListener
  public void onServerIssueStatusChanged(ServerIssueStatusChangedEvent event) {
    issueStatusChanged(event.getFinding().getRuleKey());
  }

  @EventListener
  public void onLocalOnlyIssueStatusChanged(LocalOnlyIssueStatusChangedEvent event) {
    issueStatusChanged(event.getIssue().getRuleKey());
  }

  private void issueStatusChanged(String ruleKey) {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.addIssueStatusChanged(ruleKey));
    }
  }

  public void newCodeFocusChanged() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementNewCodeFocusChange);
    }
  }

  @PreDestroy
  public void close() {
    if ((!MoreExecutors.shutdownAndAwaitTermination(scheduledExecutor, 1, TimeUnit.SECONDS)) && (InternalDebug.isEnabled())) {
      LOG.error("Failed to stop telemetry executor");
    }
  }
}
