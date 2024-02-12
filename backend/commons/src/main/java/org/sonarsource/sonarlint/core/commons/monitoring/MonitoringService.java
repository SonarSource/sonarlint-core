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
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.commons.SonarLintCoreVersion;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

@Named
@Singleton
public class MonitoringService {
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
    var releaseVersion = SonarLintCoreVersion.get();
    var environment = "dogfood";
    var productKey = initializeParams.getProductKey();
    var sonarQubeForIDEVersion = initializeParams.getSonarQubeForIdeVersion();
    var ideVersion = initializeParams.getIdeVersion();
    var platform = SystemUtils.OS_NAME;
    var architecture = SystemUtils.OS_ARCH;

    var sentryOptions = new SentryOptions();
    sentryOptions.setDsn("https://ad1c1fe3cb2b12fc2d191ecd25f89866@o1316750.ingest.us.sentry.io/4508201175089152");
    sentryOptions.setRelease(releaseVersion);
    sentryOptions.setEnvironment(environment);
    sentryOptions.setTag("productKey", productKey);
    sentryOptions.setTag("sonarQubeForIDEVersion", sonarQubeForIDEVersion);
    sentryOptions.setTag("ideVersion", ideVersion);
    sentryOptions.setTag("platform", platform);
    sentryOptions.setTag("architecture", architecture);
    sentryOptions.addInAppInclude("org.sonarsource.sonarlint");
    return sentryOptions;
  }
}
