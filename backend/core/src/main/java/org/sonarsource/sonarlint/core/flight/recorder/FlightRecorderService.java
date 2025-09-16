/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.flight.recorder;

import io.sentry.Attachment;
import io.sentry.Hint;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.MonitoringService;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.flightrecorder.FlightRecorderStartedParams;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

public class FlightRecorderService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String[] PROXY_PROPERTIES = {
    "java.net.useSystemProxies",
    "http.proxyHost",
    "http.proxyPort",
    "https.proxyHost",
    "https.proxyPort",
    "http.nonProxyHosts"
  };

  private final boolean enabled;
  private final FlightRecorderSession session;
  private final TelemetryService telemetryService;
  private final SonarLintRpcClient client;

  public FlightRecorderService(InitializeParams initializeParams, FlightRecorderSession session, MonitoringService monitoringService, TelemetryService telemetryService,
    SonarLintRpcClient client) {
    this.enabled = initializeParams.getBackendCapabilities().contains(BackendCapability.FLIGHT_RECORDER)
      && monitoringService.isActive();
    this.session = session;
    this.telemetryService = telemetryService;
    this.client = client;
  }

  @PostConstruct
  public void launch() {
    if (!enabled) {
      LOG.debug("Not starting Flight Recorder service");
      return;
    }

    LOG.info("Starting Flight Recorder service for session ", session);
    telemetryService.flightRecorderStarted();

    var startEvent = newInfoEvent("Flight recorder started");
    var defaultLocale = Locale.getDefault();
    startEvent.getContexts().put("Default Locale", Map.of(
      "Display Name", defaultLocale.getDisplayName(),
      "Language", defaultLocale.getLanguage(),
      "Country", defaultLocale.getCountry()
    ));
    var proxyProperties = getProxyProperties();
    if (!proxyProperties.isEmpty()) {
      startEvent.getContexts().put("Proxy Settings", getProxyProperties());
    }
    Sentry.captureEvent(startEvent);

    client.flightRecorderStarted(new FlightRecorderStartedParams(session.sessionId().toString()));
  }

  @PreDestroy
  public void shutdown() {
    if (!enabled) {
      return;
    }

    sendInfoEvent("Flight recorder stopped");
  }

  public void captureThreadDump() {
    if (!enabled) {
      LOG.debug("Ignoring thread dump capture request, not in a flight recording session");
      return;
    }

    var threadDump = new StringBuilder();
    var threadBean = ManagementFactory.getThreadMXBean();
    Arrays.stream(threadBean.dumpAllThreads(true, true))
      .forEach(t -> threadDump.append(t.toString()).append(System.lineSeparator()));
    var threadDumpAttachment = new Attachment(threadDump.toString().getBytes(StandardCharsets.UTF_8), "threads.txt");

    Sentry.captureEvent(newInfoEvent("Captured thread dump"), Hint.withAttachment(threadDumpAttachment));
  }

  private static void sendInfoEvent(String message) {
    var flightRecorderStarted = newInfoEvent(message);
    Sentry.captureEvent(flightRecorderStarted);
  }

  private static SentryEvent newInfoEvent(String eventMessage) {
    var flightRecorderStarted = new SentryEvent();
    flightRecorderStarted.setLevel(SentryLevel.INFO);
    var message = new Message();
    message.setMessage(eventMessage);
    flightRecorderStarted.setMessage(message);
    return flightRecorderStarted;
  }

  private static Map<String, String> getProxyProperties() {
    var proxySettings = new LinkedHashMap<String, String>();
    Stream.of(PROXY_PROPERTIES).forEach(propKey -> {
      var propValue = System.getProperty(propKey);
      if (propValue != null) {
        proxySettings.put(propKey, propValue);
      }
    });
    return proxySettings;
  }
}
