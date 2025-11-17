/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.commons.monitoring;

import io.sentry.Hint;
import io.sentry.ScopeType;
import io.sentry.Sentry;
import io.sentry.SentryAttributeType;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import jakarta.inject.Inject;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.commons.SonarLintCoreVersion;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class MonitoringService {

  public static final String DSN_PROPERTY = "sonarlint.internal.monitoring.dsn";
  private static final String DSN_DEFAULT = "https://ad1c1fe3cb2b12fc2d191ecd25f89866@o1316750.ingest.us.sentry.io/4508201175089152";

  public static final String TRACES_SAMPLE_RATE_PROPERTY = "sonarlint.internal.monitoring.tracesSampleRate";
  private static final double TRACES_SAMPLE_RATE_DEFAULT = 0.0001D;
  private static final double TRACES_SAMPLE_RATE_DOGFOOD_DEFAULT = 0.01D;
  private static final double TRACES_SAMPLE_RATE_FLIGHT_RECORDER = 1D;

  private static final String ENVIRONMENT_FLIGHT_RECORDER = "flight_recorder";
  private static final String ENVIRONMENT_DOGFOOD = "dogfood";

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final MonitoringInitializationParams initializeParams;
  private final DogfoodEnvironmentDetectionService dogfoodEnvDetectionService;

  private boolean active;

  @Inject
  public MonitoringService(MonitoringInitializationParams initializeParams, DogfoodEnvironmentDetectionService dogfoodEnvDetectionService) {
    this.initializeParams = initializeParams;
    this.dogfoodEnvDetectionService = dogfoodEnvDetectionService;

    this.init();
  }

  public void init() {
    if (!initializeParams.monitoringEnabled()) {
      LOG.info("Monitoring is disabled by feature flag.");
      return;
    }

    if (shouldInitializeSentry()) {
      var sentryConfiguration = getSentryConfiguration();
      LOG.info("Initializing Sentry");
      Sentry.init(sentryConfiguration);
      active = true;
      if (initializeParams.flightRecorderEnabled()) {
        configureFlightRecorderSession();
      }
    }
  }

  private boolean shouldInitializeSentry() {
    return dogfoodEnvDetectionService.isDogfoodEnvironment() || initializeParams.flightRecorderEnabled();
  }

  public boolean isActive() {
    return active;
  }

  SentryOptions getSentryConfiguration() {
    var sentryOptions = new SentryOptions();
    sentryOptions.setDsn(getDsn());
    sentryOptions.setRelease(SonarLintCoreVersion.getLibraryVersion());
    sentryOptions.setEnvironment(getEnvironment());
    if (initializeParams.flightRecorderEnabled()) {
      sentryOptions.getLogs().setEnabled(true);
      var sessionId = new SentryLogEventAttributeValue(SentryAttributeType.STRING, initializeParams.flightRecorderSessionId().toString());
      sentryOptions.getLogs().setBeforeSend(logEvent -> {
        logEvent.getAttributes().put("user.id", sessionId);
        return logEvent;
      });
    }
    sentryOptions.setTag("productKey", initializeParams.productKey());
    sentryOptions.setTag("sonarQubeForIDEVersion", initializeParams.sonarQubeForIdeVersion());
    sentryOptions.setTag("ideVersion", initializeParams.ideVersion());
    sentryOptions.setTag("platform", SystemUtils.OS_NAME);
    sentryOptions.setTag("architecture", SystemUtils.OS_ARCH);
    sentryOptions.addInAppInclude("org.sonarsource.sonarlint");
    sentryOptions.setTracesSampleRate(getTracesSampleRate());
    addCaptureIgnoreRule(sentryOptions, "(?s)com\\.sonar\\.sslr\\.api\\.RecognitionException.*");
    addCaptureIgnoreRule(sentryOptions, "(?s)com\\.sonar\\.sslr\\.impl\\.LexerException.*");
    sentryOptions.setBeforeSend(MonitoringService::beforeSend);
    sentryOptions.setBeforeSendTransaction(MonitoringService::beforeSend);
    return sentryOptions;
  }

  private String getEnvironment() {
    if (initializeParams.flightRecorderEnabled()) {
      return ENVIRONMENT_FLIGHT_RECORDER;
    }
    return ENVIRONMENT_DOGFOOD;
  }

  private static <T extends SentryBaseEvent> T beforeSend(T event, Hint hint) {
    event.setServerName(null);
    return event;
  }

  private void configureFlightRecorderSession() {
    var user = new User();
    user.setId(initializeParams.flightRecorderSessionId().toString());
    Sentry.configureScope(ScopeType.GLOBAL, scope -> scope.setUser(user));
  }

  private static String getDsn() {
    return System.getProperty(DSN_PROPERTY, DSN_DEFAULT);
  }

  private double getTracesSampleRate() {
    try {
      var sampleRateFromSystemProperty = System.getProperty(TRACES_SAMPLE_RATE_PROPERTY);
      var parsedSampleRate = Double.parseDouble(sampleRateFromSystemProperty);
      LOG.debug("Overriding trace sample rate with value from system property: {}", parsedSampleRate);
      return parsedSampleRate;
    } catch (RuntimeException e) {
      var sampleRate = TRACES_SAMPLE_RATE_DEFAULT;
      if (dogfoodEnvDetectionService.isDogfoodEnvironment()) {
        sampleRate = TRACES_SAMPLE_RATE_DOGFOOD_DEFAULT;
      }
      if (initializeParams.flightRecorderEnabled()) {
        sampleRate = TRACES_SAMPLE_RATE_FLIGHT_RECORDER;
      }
      LOG.debug("Using default trace sample rate: {}", sampleRate);
      return sampleRate;
    }
  }

  /**
   * To ignore exceptions, it's better to use {@link SentryOptions#addIgnoredExceptionForType}, but it accepts Class type
   * and this is the workaround for the case when Exception class is not in the classpath
   *
   * @param regex this should be the regex satisfying java.util.regex.Pattern spec
   */
  private static void addCaptureIgnoreRule(SentryOptions sentryOptions, String regex) {
    sentryOptions.addIgnoredError(regex);
  }

  public Trace newTrace(String name, String operation) {
    return Trace.begin(name, operation);
  }
}
