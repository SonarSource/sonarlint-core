/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.monitoring;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import jakarta.inject.Inject;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.commons.SonarLintCoreVersion;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class MonitoringService {

  private static final String TRACES_SAMPLE_RATE_PROPERTY = "sonarlint.internal.monitoring.tracesSampleRate";
  private static final double TRACES_SAMPLE_RATE_DEFAULT = 0.01D;

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final MonitoringInitializationParams initializeParams;
  private final DogfoodEnvironmentDetectionService dogfoodEnvDetectionService;

  @Inject
  public MonitoringService(MonitoringInitializationParams initializeParams, DogfoodEnvironmentDetectionService dogfoodEnvDetectionService) {
    this.initializeParams = initializeParams;
    this.dogfoodEnvDetectionService = dogfoodEnvDetectionService;

    this.init();
  }

  public void init() {
    var sentryConfiguration = getSentryConfiguration();

    if (!initializeParams.isEnabled()) {
      LOG.info("Monitoring is disabled by feature flag.");
      return;
    }
    if (dogfoodEnvDetectionService.isDogfoodEnvironment()) {
      LOG.info("Initializing Sentry");
      Sentry.init(sentryConfiguration);
    }
  }

  SentryOptions getSentryConfiguration() {
    var sentryOptions = new SentryOptions();
    sentryOptions.setDsn("https://ad1c1fe3cb2b12fc2d191ecd25f89866@o1316750.ingest.us.sentry.io/4508201175089152");
    sentryOptions.setRelease(SonarLintCoreVersion.get());
    sentryOptions.setEnvironment("dogfood");
    sentryOptions.setTag("productKey", initializeParams.getProductKey());
    sentryOptions.setTag("sonarQubeForIDEVersion", initializeParams.getSonarQubeForIdeVersion());
    sentryOptions.setTag("ideVersion", initializeParams.getIdeVersion());
    sentryOptions.setTag("platform", SystemUtils.OS_NAME);
    sentryOptions.setTag("architecture", SystemUtils.OS_ARCH);
    sentryOptions.addInAppInclude("org.sonarsource.sonarlint");
    sentryOptions.setTracesSampleRate(getTracesSampleRate());
    return sentryOptions;
  }

  static double getTracesSampleRate() {
    try {
      var sampleRateFromSystemProperty = System.getProperty(TRACES_SAMPLE_RATE_PROPERTY);
      return Double.parseDouble(sampleRateFromSystemProperty);
    } catch (RuntimeException e) {
      return TRACES_SAMPLE_RATE_DEFAULT;
    }
  }

  public Trace newTrace(String name, String operation) {
    return Trace.begin(name, operation);
  }
}
